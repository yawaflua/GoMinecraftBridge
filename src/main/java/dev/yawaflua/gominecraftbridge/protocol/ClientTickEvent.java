package dev.yawaflua.gominecraftbridge.protocol;

public record ClientTickEvent(
		long tick,
		long timestampUnixMilli,
		boolean connected,
		String serverAddress,
		String playerUuid,
		String playerName,
		String dimension,
		boolean hasPosition,
		double x,
		double y,
		double z,
		long dayTime,
		int fps,
		int guiWidth,
		int guiHeight,
		ClientScreenState screen,
		ClientTarget target,
		ClientMovementKeys keys,
		ClientPlayerState player
) {
	public ClientTickEvent(long tick, long timestampUnixMilli, boolean connected, String serverAddress,
			String playerUuid, String playerName, String dimension) {
		this(tick, timestampUnixMilli, connected, serverAddress, playerUuid, playerName, dimension,
				false, 0, 0, 0, 0, 0, 0, 0, null, null, ClientMovementKeys.NONE, null);
	}
}
