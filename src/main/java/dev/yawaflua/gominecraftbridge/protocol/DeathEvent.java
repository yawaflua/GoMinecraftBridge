package dev.yawaflua.gominecraftbridge.protocol;

public record DeathEvent(
		EntitySnapshot entity,
		String damageType,
		String attackerUuid,
		long timestampUnixMilli
) {
}
