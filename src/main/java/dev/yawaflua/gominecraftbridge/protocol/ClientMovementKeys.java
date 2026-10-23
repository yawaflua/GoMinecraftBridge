package dev.yawaflua.gominecraftbridge.protocol;

public record ClientMovementKeys(
		boolean forward,
		boolean back,
		boolean left,
		boolean right,
		boolean jump,
		boolean sneak,
		boolean sprint,
		boolean attack,
		boolean use
) {
	public static final ClientMovementKeys NONE = new ClientMovementKeys(
			false, false, false, false, false, false, false, false, false
	);
}
