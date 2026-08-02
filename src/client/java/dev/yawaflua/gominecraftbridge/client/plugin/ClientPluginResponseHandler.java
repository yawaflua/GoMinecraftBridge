package dev.yawaflua.gominecraftbridge.client.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.yawaflua.gominecraftbridge.client.ClientHudState;
import dev.yawaflua.gominecraftbridge.client.ClientScreenCaptureController;
import dev.yawaflua.gominecraftbridge.client.ClientScreenController;
import dev.yawaflua.gominecraftbridge.client.ClientProtocolInput;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.protocol.ActionRequest;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import dev.yawaflua.gominecraftbridge.protocol.HudScene;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.time.Instant;

/** Executes effects returned by a native plugin inside the Minecraft client. */
public final class ClientPluginResponseHandler {
	private static final int MAX_SYSTEM_CALL_CHAIN = 32;

	private final Logger logger;
	private final ClientHudState hud;
	private final ClientScreenController screens;
	private final ClientScreenCaptureController captures;

	public ClientPluginResponseHandler(
			Logger logger,
			ClientHudState hud,
			ClientScreenController screens,
			ClientScreenCaptureController captures
	) {
		this.logger = logger;
		this.hud = hud;
		this.screens = screens;
		this.captures = captures;
	}

	public void invoke(LoadedPlugin plugin, Protocol.Operation operation, Object input, Minecraft client) {
		try {
			process(plugin, plugin.invoke(operation, input), client, 0);
		} catch (RuntimeException exception) {
			disable(plugin, operation + " failed", exception);
		}
	}

	public void process(LoadedPlugin plugin, PluginResponse response, Minecraft client) {
		process(plugin, response, client, 0);
	}

	private void process(LoadedPlugin plugin, PluginResponse response, Minecraft client, int systemCallDepth) {
		for (PluginLog log : response.logs()) {
			writeLog(plugin, log);
		}
		if (response.snapshot() != null) {
			bridgeLog(plugin, "warn", "Snapshot subscriptions are unavailable in the client runtime");
		}
		if (response.isPanic()) {
			disable(plugin, "client callback panicked: " + response.error(), null);
			return;
		}
		if (response.isError()) {
			bridgeLog(plugin, "error", "Client callback returned an error: " + response.error());
		}

		for (ActionRequest action : response.actions()) {
			executeAction(plugin, action, client);
		}

		if (response.systemCalls().isEmpty()) {
			return;
		}
		if (systemCallDepth >= MAX_SYSTEM_CALL_CHAIN) {
			disable(plugin, "client system call chain exceeded " + MAX_SYSTEM_CALL_CHAIN, null);
			return;
		}
		for (var request : response.systemCalls()) {
			SystemCallResult unavailable = new SystemCallResult(
					request.id(), request.name(), false, JsonNull.INSTANCE,
					"System calls are unavailable in the client runtime"
			);
			try {
				process(plugin, plugin.invoke(
						Protocol.Operation.SYSTEM_CALL_RESULT, ClientProtocolInput.scoped(unavailable)
				), client, systemCallDepth + 1);
			} catch (RuntimeException exception) {
				disable(plugin, "client system call result callback failed", exception);
				return;
			}
		}
	}

	public void disable(LoadedPlugin plugin, String reason, Throwable throwable) {
		plugin.disable();
		this.hud.clear(plugin.metadata().id());
		this.captures.clear(plugin);
		this.screens.clear(plugin, Minecraft.getInstance());
		bridgeLog(plugin, "error", "Plugin disabled: " + reason
				+ (throwable == null ? "" : " — " + rootMessage(throwable)));
		if (throwable == null) {
			this.logger.error("Disabled client Go plugin {}: {}", plugin.metadata().id(), reason);
		} else {
			this.logger.error("Disabled client Go plugin {}: {}", plugin.metadata().id(), reason, throwable);
		}
	}

	public void bridgeLog(LoadedPlugin plugin, String level, String message) {
		plugin.appendLog(new PluginLog("client-bridge", level, message, Instant.now().toEpochMilli()));
	}

	public static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private void executeAction(LoadedPlugin plugin, ActionRequest action, Minecraft client) {
		if ("minecraft:client.screen.open".equals(action.type())) {
			try {
				ClientScreenSpec screen = ProtocolJson.GSON.fromJson(action.payload(), ClientScreenSpec.class);
				this.screens.open(plugin, screen, client);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "warn", "Rejected malformed client screen: " + rootMessage(exception));
			}
			return;
		}
		if ("minecraft:client.screen.close".equals(action.type())) {
			JsonElement id = action.payload() == null ? null : action.payload().get("id");
			if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
				bridgeLog(plugin, "warn", "Rejected client screen close without an id");
			} else {
				this.screens.close(plugin, id.getAsString(), client, true);
			}
			return;
		}
		if ("minecraft:client.screen.capture".equals(action.type())) {
			this.captures.request(plugin);
			return;
		}
		if ("minecraft:client.hud.set".equals(action.type())) {
			try {
				HudScene scene = ProtocolJson.GSON.fromJson(action.payload(), HudScene.class);
				if (scene == null) {
					throw new IllegalArgumentException("HUD scene is missing");
				}
				this.hud.replace(plugin.metadata().id(), scene);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "warn", "Rejected malformed HUD scene: " + rootMessage(exception));
			}
			return;
		}
		if ("minecraft:client.hud.upsert".equals(action.type())) {
			try {
				JsonElement rawElement = action.payload() == null ? null : action.payload().get("element");
				HudElementDto element = ProtocolJson.GSON.fromJson(rawElement, HudElementDto.class);
				if (element == null) {
					throw new IllegalArgumentException("HUD element is missing");
				}
				this.hud.upsert(plugin.metadata().id(), element);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "warn", "Rejected malformed HUD element: " + rootMessage(exception));
			}
			return;
		}
		if ("minecraft:client.hud.remove".equals(action.type())) {
			JsonElement id = action.payload() == null ? null : action.payload().get("id");
			if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
				bridgeLog(plugin, "warn", "Rejected HUD removal without an element id");
			} else {
				this.hud.remove(plugin.metadata().id(), id.getAsString());
			}
			return;
		}
		if (!"minecraft:client.chat.display".equals(action.type())) {
			bridgeLog(plugin, "warn", "Rejected action in client runtime: " + action.type());
			return;
		}
		JsonElement message = action.payload() == null ? null : action.payload().get("message");
		if (message == null || !message.isJsonPrimitive() || !message.getAsJsonPrimitive().isString()) {
			bridgeLog(plugin, "warn", "Rejected malformed client chat display action");
			return;
		}
		if (client.player == null) {
			bridgeLog(plugin, "warn", "Cannot display client message outside a world");
			return;
		}
		client.player.sendSystemMessage(Component.literal(message.getAsString()));
	}

	private void writeLog(LoadedPlugin plugin, PluginLog log) {
		plugin.appendLog(log);
		String message = "[Go/client/" + plugin.metadata().id() + "/" + log.stream() + "] " + log.message();
		switch (log.level() == null ? "info" : log.level()) {
			case "trace" -> this.logger.trace(message);
			case "debug" -> this.logger.debug(message);
			case "warn" -> this.logger.warn(message);
			case "error" -> this.logger.error(message);
			default -> this.logger.info(message);
		}
	}
}
