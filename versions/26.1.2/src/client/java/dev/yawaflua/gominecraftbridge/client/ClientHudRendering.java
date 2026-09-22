package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
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
				(graphics, deltaTracker) -> render(state, graphics, false)
		);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) ->
						render(state, graphics, true)
				)
		);
	}

	private static void render(ClientHudState state, GuiGraphicsExtractor graphics, boolean screenPass) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		boolean screenOpen = client.screen != null;
		for (HudElementDto element : state.elements()) {
			if (screenPass ? !element.overScreens() : element.overScreens() && screenOpen) {
				continue;
			}
			boolean text = "text".equals(element.type());
			int width = text && element.width() == 0 ? font.width(element.text()) : element.width();
			int height = text && element.height() == 0 ? font.lineHeight : element.height();
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
