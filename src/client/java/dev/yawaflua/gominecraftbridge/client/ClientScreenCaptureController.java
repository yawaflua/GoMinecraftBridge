package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.host.PluginState;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public final class ClientScreenCaptureController implements AutoCloseable {
	private static final int HEADER_SIZE = 24;
	private static final int MAX_PIXELS = 16_000_000;
	private final Map<String, LoadedPlugin> pending = new LinkedHashMap<>();
	private final ExecutorService encoder = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "GBM-Screen-Capture");
		thread.setDaemon(true);
		return thread;
	});
	private final BiConsumer<LoadedPlugin, byte[]> delivery;
	private final BiConsumer<LoadedPlugin, String> warnings;
	private volatile boolean capturing;

	public ClientScreenCaptureController(
			BiConsumer<LoadedPlugin, byte[]> delivery,
			BiConsumer<LoadedPlugin, String> warnings
	) {
		this.delivery = delivery;
		this.warnings = warnings;
	}

	public void request(LoadedPlugin plugin) {
		this.pending.putIfAbsent(plugin.metadata().id(), plugin);
	}

	public void tick(Minecraft client) {
		if (this.capturing || this.pending.isEmpty()) {
			return;
		}
		List<LoadedPlugin> recipients = new ArrayList<>(this.pending.values());
		this.pending.clear();
		this.capturing = true;
		ClientFramebufferCapture.capture(client, pixels -> encode(client, recipients, pixels), error -> {
			this.capturing = false;
			for (LoadedPlugin plugin : recipients) {
				this.warnings.accept(plugin, error);
			}
		});
	}

	public void clear(LoadedPlugin plugin) {
		this.pending.remove(plugin.metadata().id());
	}

	@Override
	public void close() {
		this.pending.clear();
		this.encoder.shutdownNow();
	}

	private void encode(Minecraft client, List<LoadedPlugin> recipients, ClientFramebufferPixels pixels) {
		long pixelCount = (long) pixels.width() * pixels.height();
		if (pixels.width() <= 0 || pixels.height() <= 0 || pixelCount > MAX_PIXELS
				|| pixels.pixels().length != pixelCount) {
			this.capturing = false;
			for (LoadedPlugin plugin : recipients) {
				this.warnings.accept(plugin, "Rejected invalid or oversized framebuffer capture");
			}
			return;
		}
		CompletableFuture.supplyAsync(() -> pack(pixels), this.encoder).whenComplete((frame, throwable) ->
				client.execute(() -> {
					this.capturing = false;
					if (throwable != null) {
						for (LoadedPlugin plugin : recipients) {
							this.warnings.accept(plugin, "Cannot encode framebuffer capture: " + throwable.getMessage());
						}
						return;
					}
					for (LoadedPlugin plugin : recipients) {
						if (plugin.state() == PluginState.RUNNING) {
							this.delivery.accept(plugin, frame);
						}
					}
				})
		);
	}

	static byte[] pack(ClientFramebufferPixels capture) {
		int payloadLength = Math.multiplyExact(Math.multiplyExact(capture.width(), capture.height()), 4);
		ByteBuffer output = ByteBuffer.allocate(Math.addExact(HEADER_SIZE, payloadLength)).order(ByteOrder.LITTLE_ENDIAN);
		output.put((byte) 'G').put((byte) 'M').put((byte) 'B').put((byte) 'C');
		output.put((byte) 1).put((byte) 1).putShort((short) 0);
		output.putInt(capture.width()).putInt(capture.height()).putInt(capture.width() * 4).putInt(payloadLength);
		for (int pixel : capture.pixels()) {
			if (capture.abgr()) {
				output.put((byte) pixel);
				output.put((byte) (pixel >>> 8));
				output.put((byte) (pixel >>> 16));
				output.put((byte) (pixel >>> 24));
			} else {
				output.put((byte) (pixel >>> 16));
				output.put((byte) (pixel >>> 8));
				output.put((byte) pixel);
				output.put((byte) (pixel >>> 24));
			}
		}
		return output.array();
	}
}
