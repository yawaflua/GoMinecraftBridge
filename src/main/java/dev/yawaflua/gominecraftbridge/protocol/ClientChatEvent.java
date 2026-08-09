package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ClientChatEvent(String message, List<String> clickValues, long timestampUnixMilli, String runtimeEnvironment) {
	public ClientChatEvent {
		clickValues = clickValues == null ? List.of() : List.copyOf(clickValues);
	}

	public ClientChatEvent(String message, List<String> clickValues) {
		this(message, clickValues, java.time.Instant.now().toEpochMilli(), "client");
	}
}
