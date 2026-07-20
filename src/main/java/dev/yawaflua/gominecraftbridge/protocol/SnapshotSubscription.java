package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record SnapshotSubscription(Boolean entities, List<BlockReference> blocks) {
	public SnapshotSubscription {
		blocks = blocks == null ? List.of() : List.copyOf(blocks);
	}

	public boolean includesEntities() {
		return entities == null || entities;
	}

	public static SnapshotSubscription defaults() {
		return new SnapshotSubscription(true, List.of());
	}
}
