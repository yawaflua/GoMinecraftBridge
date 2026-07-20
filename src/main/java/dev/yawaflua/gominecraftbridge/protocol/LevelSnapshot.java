package dev.yawaflua.gominecraftbridge.protocol;

public record LevelSnapshot(
		String dimension,
		long gameTime,
		long dayTime,
		boolean raining,
		boolean thundering
) {
}
