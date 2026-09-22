package dev.yawaflua.gominecraftbridge.protocol;

public record ClientTitleEvent(
		String type,
		String buttonId,
		String screen,
		long timestampUnixMilli
) {
}
