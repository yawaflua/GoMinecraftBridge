package dev.yawaflua.gominecraftbridge.host;

import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.api.SystemCallRegistry;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class BuiltInSystemCalls {
	private BuiltInSystemCalls() {
	}

	public static void register(SystemCallRegistry registry) {
		registry.register("minecraft:server.info", (context, payload) -> {
			JsonObject result = new JsonObject();
			result.addProperty("tick", context.server().getTickCount());
			result.addProperty("dedicated", context.server().isDedicatedServer());
			result.addProperty("onlinePlayers", context.server().getPlayerCount());
			return result;
		});

		registry.register("minecraft:player.get", (context, payload) -> {
			JsonObject request = payload.getAsJsonObject();
			UUID playerId = UUID.fromString(request.get("playerUuid").getAsString());
			var player = context.server().getPlayerList().getPlayer(playerId);
			if (player == null) {
				return ProtocolJson.tree(null);
			}

			JsonObject result = new JsonObject();
			result.addProperty("uuid", player.getUUID().toString());
			result.addProperty("name", player.getName().getString());
			result.addProperty("dimension", player.level().dimension().identifier().toString());
			result.addProperty("x", player.getX());
			result.addProperty("y", player.getY());
			result.addProperty("z", player.getZ());
			return result;
		});

		registry.register("minecraft:block.get", (context, payload) -> {
			JsonObject request = payload.getAsJsonObject();
			String dimension = request.get("dimension").getAsString();
			ServerLevel selected = null;

			for (ServerLevel level : context.server().getAllLevels()) {
				if (level.dimension().identifier().toString().equals(dimension)) {
					selected = level;
					break;
				}
			}

			if (selected == null) {
				throw new IllegalArgumentException("Unknown dimension " + dimension);
			}

			BlockPos position = new BlockPos(
					request.get("x").getAsInt(),
					request.get("y").getAsInt(),
					request.get("z").getAsInt()
			);
			JsonObject result = new JsonObject();
			result.addProperty("loaded", selected.isLoaded(position));
			if (selected.isLoaded(position)) {
				result.addProperty(
						"block",
						BuiltInRegistries.BLOCK.getKey(selected.getBlockState(position).getBlock()).toString()
				);
			}
			return result;
		});
	}
}
