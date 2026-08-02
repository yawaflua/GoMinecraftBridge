package dev.yawaflua.gominecraftbridge.protocol;

import java.util.Map;

public record ClientScreenEvent(
		String screenId,
		String type,
		String buttonId,
		String reason,
		Map<String, String> values
) {
	public ClientScreenEvent {
		values = values == null ? Map.of() : Map.copyOf(values);
	}
}
