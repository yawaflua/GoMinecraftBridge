package dev.yawaflua.gominecraftbridge.protocol;

public record InteractionEvent(
		String action,
		String hand,
		boolean sneaking,
		EntitySnapshot player,
		BlockSnapshot block,
		EntitySnapshot target,
		String face,
		Double hitX,
		Double hitY,
		Double hitZ,
		long timestampUnixMilli
) {
}
