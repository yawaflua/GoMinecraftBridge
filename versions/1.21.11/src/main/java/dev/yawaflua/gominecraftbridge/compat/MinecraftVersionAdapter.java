package dev.yawaflua.gominecraftbridge.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.11 implementation of the shared version adapter. */
public final class MinecraftVersionAdapter {
	private MinecraftVersionAdapter() {
	}

	public static String gameVersion() {
		return FabricLoader.getInstance()
				.getModContainer("minecraft")
				.orElseThrow(() -> new IllegalStateException("Minecraft mod container is unavailable"))
				.getMetadata()
				.getVersion()
				.getFriendlyString();
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static long dayTime(ServerLevel level) {
		return level.getDayTime();
	}

	public static long clientDayTime(Level level) {
		return level.getDayTime();
	}


	public static boolean isOperator(MinecraftServer server, ServerPlayer player) {
		return server.getPlayerList().isOp(player.nameAndId());
	}

	public static void kill(LivingEntity entity, ServerLevel level) {
		entity.kill(level);
	}
}
