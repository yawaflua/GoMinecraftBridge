package dev.yawaflua.gominecraftbridge.protocol;

public record ActionResult(
		String id,
		String type,
		boolean success,
		String error
) {
}
