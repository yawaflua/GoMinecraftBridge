package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class ClientHudRendering {
	private ClientHudRendering() {
	}

	public static void register(ClientHudState state) {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("gbm", "plugin_hud"),
				(graphics, deltaTracker) -> render(state, graphics)
		);
	}

	private static void render(ClientHudState state, GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		for (HudElementDto element : state.elements()) {
			int width = "text".equals(element.type()) ? font.width(element.text()) : element.width();
			int height = "text".equals(element.type()) ? font.lineHeight : element.height();
			var position = ClientHudLayout.position(
					element, width, height, graphics.guiWidth(), graphics.guiHeight()
			);
			switch (element.type()) {
				case "text" -> graphics.text(
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
