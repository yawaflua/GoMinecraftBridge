package dev.yawaflua.gominecraftbridge.protocol;

public record AllowDeathEvent(
		EntitySnapshot entity,
		String damageType,
		String attackerUuid,
		float damageAmount,
		long timestampUnixMilli
) {
}
