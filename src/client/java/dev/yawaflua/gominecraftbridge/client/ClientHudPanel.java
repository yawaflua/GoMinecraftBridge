package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import net.minecraft.client.gui.Font;

import java.util.List;

record ClientHudPanel(List<String> lines, int x, int y, int width, int height, int textX, int textY, int lineStep) {
	static final int ACCENT_WIDTH = 3;

	static ClientHudPanel layout(HudElementDto element, Font font, int screenWidth, int screenHeight) {
		List<String> lines = List.of(element.text().split("\n", -1));
		int padding = element.padding();
		int accent = element.accent() == 0 ? 0 : ACCENT_WIDTH + 4;
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int width = textWidth + padding * 2 + accent;
		int height = font.lineHeight * lines.size() + element.lineSpacing() * (lines.size() - 1) + padding * 2;
		var position = ClientHudLayout.position(element, width, height, screenWidth, screenHeight);
		return new ClientHudPanel(
				lines, position.x(), position.y(), width, height,
				position.x() + padding + accent, position.y() + padding, font.lineHeight + element.lineSpacing()
		);
	}
}
