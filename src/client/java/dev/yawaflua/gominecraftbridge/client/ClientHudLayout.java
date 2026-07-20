package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;

public final class ClientHudLayout {
	private ClientHudLayout() {
	}

	public static Position position(
			HudElementDto element,
			int elementWidth,
			int elementHeight,
			int screenWidth,
			int screenHeight
	) {
		String anchor = element.anchor() == null ? "top_left" : element.anchor();
		int x = switch (anchor) {
			case "top_center", "center", "bottom_center" -> screenWidth / 2 + element.x() - elementWidth / 2;
			case "top_right", "center_right", "bottom_right" -> screenWidth - element.x() - elementWidth;
			default -> element.x();
		};
		int y = switch (anchor) {
			case "center_left", "center", "center_right" -> screenHeight / 2 + element.y() - elementHeight / 2;
			case "bottom_left", "bottom_center", "bottom_right" -> screenHeight - element.y() - elementHeight;
			default -> element.y();
		};
		return new Position(x, y);
	}

	public record Position(int x, int y) {
	}
}
