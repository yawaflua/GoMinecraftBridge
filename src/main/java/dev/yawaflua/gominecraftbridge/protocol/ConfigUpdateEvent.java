package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonObject;

import java.util.Objects;

/** Complete client-side configuration saved from a native plugin's screen. */
public record ConfigUpdateEvent(JsonObject config) {
	public ConfigUpdateEvent {
		Objects.requireNonNull(config, "config");
	}
}
