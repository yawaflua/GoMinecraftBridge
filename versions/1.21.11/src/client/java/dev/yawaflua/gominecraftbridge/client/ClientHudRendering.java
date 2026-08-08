package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ClientHudRendering {
	private ClientHudRendering() {
	}

	public static void register(ClientHudState state) {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(state, graphics));
	}

	private static void render(ClientHudState state, GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			return;
		}
		Font font = client.font;
		for (HudElementDto element : state.elements()) {
			int width = "text".equals(element.type()) ? font.width(element.text()) : element.width();
			int height = "text".equals(element.type()) ? font.lineHeight : element.height();
			var position = ClientHudLayout.position(
					element, width, height, graphics.guiWidth(), graphics.guiHeight()
			);
			switch (element.type()) {
				case "text" -> graphics.drawString(
						font, element.text(), position.x(), position.y(), (int) element.color(), element.shadow()
				);
				case "rectangle" -> graphics.fill(
						position.x(), position.y(), position.x() + width, position.y() + height, (int) element.color()
				);
				default -> {
				}
			}
		}
	}
}
