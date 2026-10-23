package dev.yawaflua.gominecraftbridge.protocol;

public record ClientMenuEvent(
		String type,
		String buttonId,
		String screen,
		long timestampUnixMilli
) {
}
