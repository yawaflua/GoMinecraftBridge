package dev.yawaflua.gominecraftbridge.protocol;

/** A deliberately small snapshot that is safe to produce on the client thread. */
public record ClientTickEvent(
		long tick,
		long timestampUnixMilli,
		boolean connected,
		String serverAddress,
		String playerUuid,
		String playerName,
		String dimension
) {
}
