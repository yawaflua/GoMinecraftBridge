package dev.yawaflua.gominecraftbridge.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.yawaflua.gominecraftbridge.api.SystemCallRegistry;
import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.math.BigDecimal;
import java.util.UUID;

public final class BuiltInSystemCalls {
	private static final MinecraftSnapshotFactory SNAPSHOTS = new MinecraftSnapshotFactory();

	private BuiltInSystemCalls() {
	}

	public static void register(SystemCallRegistry registry) {
		registry.register(BuiltInSystemCall.SERVER_INFO.id(), (context, payload) -> {
			JsonObject result = new JsonObject();
			result.addProperty("tick", context.server().getTickCount());
			result.addProperty("dedicated", context.server().isDedicatedServer());
			result.addProperty("onlinePlayers", context.server().getPlayerCount());
			return result;
		});

		registry.register(BuiltInSystemCall.PLAYER_GET.id(), (context, payload) -> {
			JsonObject request = payload.getAsJsonObject();
			UUID playerId = UUID.fromString(request.get("playerUuid").getAsString());
			var player = context.server().getPlayerList().getPlayer(playerId);
			if (player == null) {
				return ProtocolJson.tree(null);
			}

			JsonObject result = new JsonObject();
			result.addProperty("uuid", player.getUUID().toString());
			result.addProperty("name", player.getName().getString());
			result.addProperty("dimension", MinecraftVersionAdapter.dimension(player.level()));
			result.addProperty("x", player.getX());
			result.addProperty("y", player.getY());
			result.addProperty("z", player.getZ());
			return result;
		});

		registry.register(BuiltInSystemCall.BLOCK_GET.id(), (context, payload) -> {
			JsonObject request = payload.getAsJsonObject();
			String dimension = request.get("dimension").getAsString();
			ServerLevel selected = null;

			for (ServerLevel level : context.server().getAllLevels()) {
				if (MinecraftVersionAdapter.dimension(level).equals(dimension)) {
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

		registry.register(BuiltInSystemCall.GET_ENTITY.id(), (context, payload) -> {
			EntityLookup lookup = parseEntityLookup(payload);
			Entity entity = findEntity(context.server().getAllLevels(), lookup);
			return entity == null ? ProtocolJson.tree(null) : ProtocolJson.tree(SNAPSHOTS.entity(entity));
		});
	}

	static EntityLookup parseEntityLookup(JsonElement payload) {
		if (payload == null || !payload.isJsonObject()) {
			throw new IllegalArgumentException("minecraft:get_entity payload must be a JSON object");
		}

		JsonObject request = payload.getAsJsonObject();
		boolean hasUuid = request.has("uuid");
		boolean hasRuntimeId = request.has("runtimeId");
		if (hasUuid == hasRuntimeId) {
			throw new IllegalArgumentException(
					"minecraft:get_entity payload must contain exactly one of uuid or runtimeId"
			);
		}

		if (hasUuid) {
			var value = request.get("uuid");
			if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
				throw new IllegalArgumentException("minecraft:get_entity uuid must be a string");
			}

			try {
				String rawUuid = primitive.getAsString();
				UUID uuid = UUID.fromString(rawUuid);
				if (!uuid.toString().equalsIgnoreCase(rawUuid)) {
					throw new IllegalArgumentException("UUID must use the canonical 8-4-4-4-12 form");
				}
				return new EntityLookup(uuid, null);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("minecraft:get_entity uuid is not a valid UUID", exception);
			}
		}

		var value = request.get("runtimeId");
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw new IllegalArgumentException("minecraft:get_entity runtimeId must be an integer");
		}

		try {
			int runtimeId = new BigDecimal(primitive.getAsString()).intValueExact();
			return new EntityLookup(null, runtimeId);
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new IllegalArgumentException("minecraft:get_entity runtimeId must be a 32-bit integer", exception);
		}
	}

	private static Entity findEntity(Iterable<ServerLevel> levels, EntityLookup lookup) {
		for (ServerLevel level : levels) {
			for (Entity entity : level.getAllEntities()) {
				if (lookup.uuid() != null && lookup.uuid().equals(entity.getUUID())) {
					return entity;
				}
				if (lookup.runtimeId() != null && lookup.runtimeId() == entity.getId()) {
					return entity;
				}
			}
		}
		return null;
	}

	record EntityLookup(UUID uuid, Integer runtimeId) {
	}
}
