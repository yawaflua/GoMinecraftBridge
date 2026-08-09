package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientChatEvent;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ClientMessageBridge {
	private ClientMessageBridge() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> forward(message));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> forward(message));
	}

	private static void forward(Component message) {
		if (message == null) {
			return;
		}
		List<String> clickValues = new ArrayList<>();
		collect(message, clickValues);
		GoMinecraftBridgeClient.runtime().chat(new ClientChatEvent(message.getString(), clickValues));
	}

	private static void collect(Component component, List<String> values) {
		ClickEvent event = component.getStyle().getClickEvent();
		if (event != null) {
			String value = clickValue(event);
			if (value != null && !value.isBlank()) {
				values.add(value);
			}
		}
		for (Component sibling : component.getSiblings()) {
			collect(sibling, values);
		}
	}

	private static String clickValue(ClickEvent event) {
		for (String accessor : List.of("getValue", "value", "command", "uri", "path", "page")) {
			try {
				Method method = event.getClass().getMethod(accessor);
				Object result = method.invoke(event);
				return result == null ? "" : result.toString();
			} catch (ReflectiveOperationException ignoredAccessor) {
			}
		}
		return "";
	}
}
