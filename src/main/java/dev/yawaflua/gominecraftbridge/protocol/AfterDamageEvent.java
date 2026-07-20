package dev.yawaflua.gominecraftbridge.protocol;

public record AfterDamageEvent(
		EntitySnapshot entity,
		String damageType,
		String attackerUuid,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked,
		long timestampUnixMilli
) {
}
