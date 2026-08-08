package dev.yawaflua.gominecraftbridge.protocol;

public record ClientKeyEvent(
		String id,
		String key,
		long timestampUnixMilli
) {
}
