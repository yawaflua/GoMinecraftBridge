package dev.yawaflua.gominecraftbridge.compat;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Minecraft-version-specific names kept behind one small source overlay. */
public final class MinecraftVersionAdapter {
	private MinecraftVersionAdapter() {
	}

	public static String gameVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static long dayTime(ServerLevel level) {
		return level.getDefaultClockTime();
	}

	public static boolean isOperator(MinecraftServer server, ServerPlayer player) {
		return server.getPlayerList().isOp(player.nameAndId());
	}
}
