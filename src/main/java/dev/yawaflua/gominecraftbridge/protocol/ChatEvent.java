package dev.yawaflua.gominecraftbridge.protocol;

public record ChatEvent(
		String playerUuid,
		String playerName,
		String message,
		long timestampUnixMilli
) {
}
