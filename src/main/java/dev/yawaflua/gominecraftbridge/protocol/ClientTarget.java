package dev.yawaflua.gominecraftbridge.protocol;

public record ClientTarget(
		String type,
		double distance,
		double x,
		double y,
		double z,
		int blockX,
		int blockY,
		int blockZ,
		String blockId,
		String side,
		String entityUuid,
		String entityType,
		String name,
		boolean player
) {
}
