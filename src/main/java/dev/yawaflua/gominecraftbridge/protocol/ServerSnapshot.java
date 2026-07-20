package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ServerSnapshot(
		long tick,
		long timestampUnixMilli,
		List<LevelSnapshot> levels,
		List<EntitySnapshot> entities,
		List<BlockSnapshot> blocks
) {
	public ServerSnapshot {
		levels = List.copyOf(levels);
		entities = List.copyOf(entities);
		blocks = List.copyOf(blocks);
	}
}
