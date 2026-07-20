package dev.yawaflua.gominecraftbridge.protocol;

public record AllowDamageEvent(
		EntitySnapshot entity,
		String damageType,
		String attackerUuid,
		float amount,
		long timestampUnixMilli
) {
}
