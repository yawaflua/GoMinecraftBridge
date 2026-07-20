package dev.yawaflua.gominecraftbridge.backend.nativeffi;

import dev.yawaflua.gominecraftbridge.backend.PluginBackend;
import dev.yawaflua.gominecraftbridge.backend.PluginInvocationException;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

public final class NativePluginBackend implements PluginBackend {
	private static final FunctionDescriptor ABI_VERSION_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT);
	private static final FunctionDescriptor CALL_DESCRIPTOR = FunctionDescriptor.of(
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS,
			ValueLayout.JAVA_LONG,
			ValueLayout.ADDRESS,
			ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor FREE_DESCRIPTOR = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

	private final Path origin;
	private final MethodHandle abiVersionHandle;
	private final MethodHandle callHandle;
	private final MethodHandle freeHandle;

	public NativePluginBackend(Path library) {
		this.origin = library.toAbsolutePath().normalize();

		try {
			Linker linker = Linker.nativeLinker();
			// Native Go runtimes are not safely unloadable. The global arena deliberately keeps
			// the library resident until JVM shutdown; deinit is a logical lifecycle operation.
			SymbolLookup symbols = SymbolLookup.libraryLookup(this.origin, Arena.global());
			this.abiVersionHandle = linker.downcallHandle(required(symbols, "gmb_abi_version"), ABI_VERSION_DESCRIPTOR);
			this.callHandle = linker.downcallHandle(required(symbols, "gmb_call"), CALL_DESCRIPTOR);
			this.freeHandle = linker.downcallHandle(required(symbols, "gmb_free"), FREE_DESCRIPTOR);
		} catch (IllegalCallerException exception) {
			throw new PluginInvocationException(
					"Native access is disabled. Start Minecraft with --enable-native-access=ALL-UNNAMED",
					exception
			);
		} catch (RuntimeException exception) {
			throw new PluginInvocationException("Cannot load native plugin " + this.origin, exception);
		}
	}

	@Override
	public int abiVersion() {
		try {
			return (int) this.abiVersionHandle.invokeExact();
		} catch (Throwable throwable) {
			throw new PluginInvocationException("Cannot read ABI version from " + this.origin, throwable);
		}
	}

	@Override
	public synchronized byte[] call(Protocol.Operation operation, byte[] input) {
		MemorySegment output = MemorySegment.NULL;

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputMemory = MemorySegment.NULL;

			if (input.length > 0) {
				inputMemory = arena.allocate(input.length);
				inputMemory.copyFrom(MemorySegment.ofArray(input));
			}

			MemorySegment outputPointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment outputLength = arena.allocate(ValueLayout.JAVA_LONG);
			outputPointer.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
			outputLength.set(ValueLayout.JAVA_LONG, 0, 0L);
			int status = (int) this.callHandle.invokeExact(
					operation.code(),
					inputMemory,
					(long) input.length,
					outputPointer,
					outputLength
			);

			output = outputPointer.get(ValueLayout.ADDRESS, 0);
			long length = outputLength.get(ValueLayout.JAVA_LONG, 0);

			if (status != 0) {
				throw new PluginInvocationException("Native entrypoint returned status " + status);
			}
			if (length < 0 || length > Protocol.MAX_RESPONSE_BYTES) {
				throw new PluginInvocationException("Native response has invalid length " + length);
			}
			if (length == 0) {
				return new byte[0];
			}
			if (output.equals(MemorySegment.NULL)) {
				throw new PluginInvocationException("Native response pointer is null for " + length + " bytes");
			}

			return output.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
		} catch (PluginInvocationException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw new PluginInvocationException("Native call failed for " + this.origin, throwable);
		} finally {
			if (!output.equals(MemorySegment.NULL)) {
				try {
					this.freeHandle.invokeExact(output);
				} catch (Throwable throwable) {
					throw new PluginInvocationException("Cannot free native response from " + this.origin, throwable);
				}
			}
		}
	}

	@Override
	public Path origin() {
		return this.origin;
	}

	private static MemorySegment required(SymbolLookup symbols, String name) {
		return symbols.find(name).orElseThrow(() -> new PluginInvocationException("Missing native symbol " + name));
	}
}
