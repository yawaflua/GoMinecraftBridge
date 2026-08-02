package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

public final class ClientProtocolInput {
	private ClientProtocolInput() {
	}

	public static JsonObject scoped(Object input) {
		JsonObject object = ProtocolJson.tree(input).getAsJsonObject();
		object.addProperty("runtimeEnvironment", "client");
		return object;
	}
}
