package dev.yawaflua.gominecraftbridge.protocol;

import java.util.Map;

public record BlockSnapshot(
		String dimension,
		int x,
		int y,
		int z,
		String block,
		Map<String, String> properties
) {
	public BlockSnapshot {
		properties = Map.copyOf(properties);
	}
}
