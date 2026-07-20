package dev.yawaflua.gominecraftbridge.host;

import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.protocol.ActionRequest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ActionExecutor {
	public void execute(MinecraftServer server, ActionRequest action) {
		JsonObject payload = action.payload();

		switch (action.type()) {
			case "minecraft:chat.broadcast" -> {
				String message = requiredString(payload, "message");
				server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
			}
			case "minecraft:chat.player" -> {
				UUID playerId = UUID.fromString(requiredString(payload, "playerUuid"));
				String message = requiredString(payload, "message");
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player == null) {
					throw new IllegalArgumentException("Player is not online: " + playerId);
				}
				player.sendSystemMessage(Component.literal(message));
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
