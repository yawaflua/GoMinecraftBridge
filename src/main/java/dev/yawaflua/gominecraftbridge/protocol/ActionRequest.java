package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonObject;

public record ActionRequest(String id, String type, JsonObject payload) {
	public ActionRequest(String type, JsonObject payload) {
		this(null, type, payload);
	}
}
