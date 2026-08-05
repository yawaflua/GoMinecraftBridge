package dev.yawaflua.gominecraftbridge.protocol;

public record PlayerConnectionEvent(
		EntitySnapshot player,
		long timestampUnixMilli
) {
}
