package dev.yawaflua.gominecraftbridge.protocol;

public record InitEvent(
		String minecraftVersion,
		boolean dedicated,
		String dataDirectory
) {
}
