package dev.yawaflua.gominecraftbridge.backend.nativeffi;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import dev.yawaflua.gominecraftbridge.backend.PluginBackend;
import dev.yawaflua.gominecraftbridge.backend.PluginInvocationException;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 21+ native backend implemented through JNA.
 *
 * <p>Minecraft 1.21.1 runs on Java 21, where the Foreign Function & Memory API
 * is still preview-only. JNA keeps the same backend usable on both Java 21 and
 * Java 25 without preview flags. Libraries are deliberately never disposed:
 * unloading a live Go runtime is unsafe.</p>
 */
public final class NativePluginBackend implements PluginBackend {
	private static final Map<Path, Object> LIBRARY_LOCKS = new ConcurrentHashMap<>();

	private final Path origin;
	private final Object libraryLock;
	private final NativeLibrary library;
	private final Function abiVersionFunction;
	private final Function callFunction;
	private final Function freeFunction;

	public NativePluginBackend(Path libraryPath) {
		this.origin = libraryPath.toAbsolutePath().normalize();
		this.libraryLock = LIBRARY_LOCKS.computeIfAbsent(this.origin, ignored -> new Object());

		try {
			this.library = NativeLibrary.getInstance(this.origin.toString());
			this.abiVersionFunction = required("gmb_abi_version");
			this.callFunction = required("gmb_call");
			this.freeFunction = required("gmb_free");
		} catch (RuntimeException exception) {
			throw new PluginInvocationException("Cannot load native plugin " + this.origin, exception);
		}
	}

	@Override
	public int abiVersion() {
		synchronized (this.libraryLock) {
			try {
				return this.abiVersionFunction.invokeInt(new Object[0]);
			} catch (RuntimeException exception) {
				throw new PluginInvocationException("Cannot read ABI version from " + this.origin, exception);
			}
		}
	}

	@Override
	public byte[] call(Protocol.Operation operation, byte[] input) {
		synchronized (this.libraryLock) {
			Memory inputMemory = null;
			Pointer output = Pointer.NULL;

			try {
				if (input.length > 0) {
					inputMemory = new Memory(input.length);
					inputMemory.write(0, input, 0, input.length);
				}

				PointerByReference outputPointer = new PointerByReference(Pointer.NULL);
				LongByReference outputLength = new LongByReference(0L);
				int status = this.callFunction.invokeInt(new Object[]{
						operation.code(),
						inputMemory == null ? Pointer.NULL : inputMemory,
						(long) input.length,
						outputPointer,
						outputLength
				});

				output = outputPointer.getValue();
				long length = outputLength.getValue();
				if (status != 0) {
					throw new PluginInvocationException("Native entrypoint returned status " + status);
				}
				if (length < 0 || length > Protocol.MAX_RESPONSE_BYTES) {
					throw new PluginInvocationException("Native response has invalid length " + length);
				}
				if (length == 0) {
					return new byte[0];
				}
				if (output == null) {
					throw new PluginInvocationException(
							"Native response pointer is null for " + length + " bytes"
					);
				}

				return output.getByteArray(0, Math.toIntExact(length));
			} catch (PluginInvocationException exception) {
				throw exception;
			} catch (RuntimeException exception) {
				throw new PluginInvocationException("Native call failed for " + this.origin, exception);
			} finally {
				if (output != null) {
					try {
						this.freeFunction.invokeVoid(new Object[]{output});
					} catch (RuntimeException exception) {
						throw new PluginInvocationException("Cannot free native response from " + this.origin, exception);
					}
				}
			}
		}
	}

	@Override
	public Path origin() {
		return this.origin;
	}

	private Function required(String name) {
		try {
			return this.library.getFunction(name);
		} catch (UnsatisfiedLinkError exception) {
			throw new PluginInvocationException("Missing native symbol " + name, exception);
		}
	}
}
