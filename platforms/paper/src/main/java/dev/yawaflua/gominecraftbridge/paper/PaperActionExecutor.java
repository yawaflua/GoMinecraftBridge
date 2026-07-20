package dev.yawaflua.gominecraftbridge.paper;

import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.protocol.ActionRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

final class PaperActionExecutor {
	void execute(ActionRequest action) {
		JsonObject payload = action.payload();
		switch (action.type()) {
			case "minecraft:chat.broadcast" ->
					Bukkit.broadcastMessage(requiredString(payload, "message"));
			case "minecraft:chat.player" -> {
				UUID playerId = UUID.fromString(requiredString(payload, "playerUuid"));
				Player player = Bukkit.getPlayer(playerId);
				if (player == null) {
					throw new IllegalArgumentException("Player is not online: " + playerId);
				}
				player.sendMessage(requiredString(payload, "message"));
			}
			default -> throw new IllegalArgumentException("Unknown plugin action " + action.type());
		}
	}

	private static String requiredString(JsonObject payload, String field) {
		if (payload == null || !payload.has(field) || !payload.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException("Action field is required: " + field);
		}
		return payload.get(field).getAsString();
	}
}
