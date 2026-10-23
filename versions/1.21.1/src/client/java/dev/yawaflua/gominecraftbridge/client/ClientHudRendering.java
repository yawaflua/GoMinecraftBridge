package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ClientHudRendering {
	private ClientHudRendering() {
	}

	public static void register(ClientHudState state) {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(state, graphics, false));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, delta) ->
						render(state, graphics, true)
				)
		);
	}

	private static void render(ClientHudState state, GuiGraphics graphics, boolean screenPass) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			return;
		}
		Font font = client.font;
		boolean screenOpen = client.screen != null;
		for (HudElementDto element : state.elements()) {
			if (screenPass ? !element.overScreens() : element.overScreens() && screenOpen) {
				continue;
			}
			if (element.hideOnScreens() && screenOpen) {
				continue;
			}
			if ("panel".equals(element.type())) {
				renderPanel(graphics, font, element);
				continue;
			}
			boolean text = "text".equals(element.type());
			int width = text && element.width() == 0 ? font.width(element.text()) : element.width();
			int height = text && element.height() == 0 ? font.lineHeight : element.height();
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

	private static void renderPanel(GuiGraphics graphics, Font font, HudElementDto element) {
		ClientHudPanel panel = ClientHudPanel.layout(element, font, graphics.guiWidth(), graphics.guiHeight());
		if (element.background() != 0) {
			graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), (int) element.background());
		}
		if (element.accent() != 0) {
			int top = panel.y() + element.padding();
			int left = panel.x() + element.padding();
			graphics.fill(left, top, left + ClientHudPanel.ACCENT_WIDTH, panel.y() + panel.height() - element.padding(), (int) element.accent());
		}
		int color = element.color() == 0 ? 0xffffffff : (int) element.color();
		for (int index = 0; index < panel.lines().size(); index++) {
			graphics.drawString(font, panel.lines().get(index), panel.textX(), panel.textY() + index * panel.lineStep(), color, element.shadow());
		}
	}
}
