package dev.yawaflua.gominecraftbridge.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.function.Consumer;

final class ClientFramebufferCapture {
	private ClientFramebufferCapture() {
	}

	static void capture(
			Minecraft client,
			Consumer<ClientFramebufferPixels> success,
			Consumer<String> failure
	) {
		if (client == null || client.getMainRenderTarget() == null) {
			failure.accept("Framebuffer capture is unavailable");
			return;
		}
		NativeImage image = null;
		try {
			image = Screenshot.takeScreenshot(client.getMainRenderTarget());
			success.accept(new ClientFramebufferPixels(
					image.getWidth(), image.getHeight(), image.getPixelsRGBA(), true
			));
		} catch (RuntimeException exception) {
			failure.accept("Framebuffer capture failed: " + exception.getMessage());
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}
}
