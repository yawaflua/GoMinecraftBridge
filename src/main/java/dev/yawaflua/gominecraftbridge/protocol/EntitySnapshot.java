package dev.yawaflua.gominecraftbridge.protocol;

public record EntitySnapshot(
		int runtimeId,
		String uuid,
		String type,
		String name,
		String dimension,
		double x,
		double y,
		double z,
		float yaw,
		float pitch,
		double velocityX,
		double velocityY,
		double velocityZ,
		boolean alive,
		boolean player,
		Float health,
		Float maxHealth
) {
}
