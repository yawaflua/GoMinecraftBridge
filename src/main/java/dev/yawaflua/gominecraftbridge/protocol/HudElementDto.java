package dev.yawaflua.gominecraftbridge.protocol;

/** A client-local retained HUD primitive supplied by a Go plugin. */
public record HudElementDto(
		String id,
		String type,
		int x,
		int y,
		int width,
		int height,
		String text,
		long color,
		boolean shadow,
		String anchor,
		long durationMillis
) {
}
