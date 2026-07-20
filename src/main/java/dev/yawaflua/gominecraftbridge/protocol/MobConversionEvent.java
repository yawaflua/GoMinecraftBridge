package dev.yawaflua.gominecraftbridge.protocol;

public record MobConversionEvent(
		EntitySnapshot previous,
		EntitySnapshot converted,
		long timestampUnixMilli
) {
}
