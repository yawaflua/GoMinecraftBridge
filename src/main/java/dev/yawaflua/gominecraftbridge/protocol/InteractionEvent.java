package dev.yawaflua.gominecraftbridge.protocol;

public record InteractionEvent(
		String action,
		String hand,
		boolean sneaking,
		boolean sprinting,
		EntitySnapshot player,
		BlockSnapshot block,
		EntitySnapshot target,
		String face,
		Double hitX,
		Double hitY,
		Double hitZ,
		java.util.List<String> targetTexts,
		long timestampUnixMilli
) {
	public InteractionEvent(String action, String hand, boolean sneaking, boolean sprinting, EntitySnapshot player,
			BlockSnapshot block, EntitySnapshot target, String face, Double hitX, Double hitY, Double hitZ,
			long timestampUnixMilli) {
		this(action, hand, sneaking, sprinting, player, block, target, face, hitX, hitY, hitZ,
				java.util.List.of(), timestampUnixMilli);
	}

	public InteractionEvent(String action, String hand, boolean sneaking, EntitySnapshot player,
			BlockSnapshot block, EntitySnapshot target, String face, Double hitX, Double hitY, Double hitZ,
			long timestampUnixMilli) {
		this(action, hand, sneaking, false, player, block, target, face, hitX, hitY, hitZ,
				java.util.List.of(), timestampUnixMilli);
	}

	public InteractionEvent {
		targetTexts = targetTexts == null ? java.util.List.of() : java.util.List.copyOf(targetTexts);
	}
}
