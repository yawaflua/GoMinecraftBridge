package dev.yawaflua.gominecraftbridge.paper;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

final class PaperSystemCalls {
	private final PaperSnapshotFactory snapshots;

	PaperSystemCalls(PaperSnapshotFactory snapshots) {
		this.snapshots = snapshots;
	}

	JsonElement execute(String name, JsonElement payload, long tick) {
		return switch (name) {
			case "minecraft:server.info" -> serverInfo(tick);
			case "minecraft:player.get" -> playerGet(payload);
			case "minecraft:block.get" -> blockGet(payload);
			case "minecraft:get_entity" -> entityGet(payload);
			default -> throw new IllegalArgumentException("Unknown system call " + name);
		};
	}

	private JsonElement serverInfo(long tick) {
		JsonObject result = new JsonObject();
		result.addProperty("tick", tick);
		result.addProperty("dedicated", true);
		result.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
		result.addProperty("maxPlayers", Bukkit.getMaxPlayers());
		result.addProperty("minecraftVersion", Bukkit.getMinecraftVersion());
		result.addProperty("server", Bukkit.getVersion());
		return result;
	}

	private JsonElement playerGet(JsonElement payload) {
		JsonObject request = object(payload, "minecraft:player.get");
		UUID playerId = UUID.fromString(requiredString(request, "playerUuid"));
		Player player = Bukkit.getPlayer(playerId);
		if (player == null) {
			return JsonNull.INSTANCE;
		}
		JsonObject result = new JsonObject();
		result.addProperty("uuid", player.getUniqueId().toString());
		result.addProperty("name", player.getName());
		result.addProperty("dimension", player.getWorld().getKey().toString());
		result.addProperty("x", player.getLocation().getX());
		result.addProperty("y", player.getLocation().getY());
		result.addProperty("z", player.getLocation().getZ());
		return result;
	}

	private JsonElement blockGet(JsonElement payload) {
		JsonObject request = object(payload, "minecraft:block.get");
		String dimension = requiredString(request, "dimension");
		NamespacedKey key = NamespacedKey.fromString(dimension);
		World world = key == null ? null : Bukkit.getWorld(key);
		if (world == null) {
			throw new IllegalArgumentException("Unknown dimension " + dimension);
		}
		int x = requiredInt(request, "x");
		int y = requiredInt(request, "y");
		int z = requiredInt(request, "z");
		boolean loaded = world.isChunkLoaded(x >> 4, z >> 4);
		JsonObject result = new JsonObject();
		result.addProperty("loaded", loaded);
		if (loaded) {
			Block block = world.getBlockAt(x, y, z);
			result.addProperty("block", block.getType().getKey().toString());
		}
		return result;
	}

	private JsonElement entityGet(JsonElement payload) {
		JsonObject request = object(payload, "minecraft:get_entity");
		boolean hasUuid = request.has("uuid");
		boolean hasRuntimeId = request.has("runtimeId");
		if (hasUuid == hasRuntimeId) {
			throw new IllegalArgumentException(
					"minecraft:get_entity payload must contain exactly one of uuid or runtimeId"
			);
		}

		Entity selected = null;
		if (hasUuid) {
			JsonElement value = request.get("uuid");
			if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
				throw new IllegalArgumentException("minecraft:get_entity uuid must be a string");
			}
			UUID uuid;
			try {
				String raw = primitive.getAsString();
				uuid = UUID.fromString(raw);
				if (!uuid.toString().equalsIgnoreCase(raw)) {
					throw new IllegalArgumentException("non-canonical UUID");
				}
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("minecraft:get_entity uuid is not a valid UUID", exception);
			}
			for (World world : Bukkit.getWorlds()) {
				selected = world.getEntity(uuid);
				if (selected != null) {
					break;
				}
			}
		} else {
			int runtimeId = exactInt(request.get("runtimeId"), "minecraft:get_entity runtimeId");
			for (World world : Bukkit.getWorlds()) {
				for (Entity entity : world.getEntities()) {
					if (entity.getEntityId() == runtimeId) {
						selected = entity;
						break;
					}
				}
				if (selected != null) {
					break;
				}
			}
		}
		return selected == null ? JsonNull.INSTANCE : ProtocolJson.tree(this.snapshots.entity(selected));
	}

	private static JsonObject object(JsonElement payload, String call) {
		if (payload == null || !payload.isJsonObject()) {
			throw new IllegalArgumentException(call + " payload must be a JSON object");
		}
		return payload.getAsJsonObject();
	}

	private static String requiredString(JsonObject object, String field) {
		JsonElement value = object.get(field);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException(field + " must be a string");
		}
		return value.getAsString();
	}

	private static int requiredInt(JsonObject object, String field) {
		return exactInt(object.get(field), field);
	}

	private static int exactInt(JsonElement value, String field) {
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		try {
			return new BigDecimal(primitive.getAsString()).intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
		}
	}
}
