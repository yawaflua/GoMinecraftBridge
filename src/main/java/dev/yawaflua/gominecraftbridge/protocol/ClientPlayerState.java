package dev.yawaflua.gominecraftbridge.protocol;

public record ClientPlayerState(
		float health,
		float maxHealth,
		int food,
		float yaw,
		float pitch,
		boolean sneaking,
		boolean sprinting,
		ClientItem mainHand,
		ClientItem offHand
) {
}
