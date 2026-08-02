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
		return position(element.anchor(), element.x(), element.y(), elementWidth, elementHeight, screenWidth, screenHeight);
	}

	public static Position position(
			String requestedAnchor,
			int requestedX,
			int requestedY,
			int elementWidth,
			int elementHeight,
			int screenWidth,
			int screenHeight
	) {
		String anchor = requestedAnchor == null || requestedAnchor.isBlank() ? "top_left" : requestedAnchor;
		int x = switch (anchor) {
			case "top_center", "center", "bottom_center" -> screenWidth / 2 + requestedX - elementWidth / 2;
			case "top_right", "center_right", "bottom_right" -> screenWidth - requestedX - elementWidth;
			default -> requestedX;
		};
		int y = switch (anchor) {
			case "center_left", "center", "center_right" -> screenHeight / 2 + requestedY - elementHeight / 2;
			case "bottom_left", "bottom_center", "bottom_right" -> screenHeight - requestedY - elementHeight;
			default -> requestedY;
		};
		return new Position(x, y);
	}

	public record Position(int x, int y) {
	}
}
