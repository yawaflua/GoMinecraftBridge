package dev.yawaflua.gominecraftbridge.compat;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.1 implementation of the shared version adapter. */
public final class MinecraftVersionAdapter {
	private MinecraftVersionAdapter() {
	}

	public static String gameVersion() {
		return SharedConstants.getCurrentVersion().getName();
	}

	public static String dimension(Level level) {
		return level.dimension().location().toString();
	}

	public static long dayTime(ServerLevel level) {
		return level.getDayTime();
	}

	public static boolean isOperator(MinecraftServer server, ServerPlayer player) {
		return server.getPlayerList().isOp(player.getGameProfile());
	}
}
