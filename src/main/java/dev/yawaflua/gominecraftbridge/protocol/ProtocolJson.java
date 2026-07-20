package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.nio.charset.StandardCharsets;

public final class ProtocolJson {
	public static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.serializeNulls()
			.create();

	private ProtocolJson() {
	}

	public static byte[] encode(Object value) {
		if (value == null) {
			return new byte[0];
		}

		return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
	}

	public static <T> T decode(byte[] value, Class<T> type) {
		return GSON.fromJson(new String(value, StandardCharsets.UTF_8), type);
	}

	public static JsonElement tree(Object value) {
		return value == null ? JsonNull.INSTANCE : GSON.toJsonTree(value);
	}
}
