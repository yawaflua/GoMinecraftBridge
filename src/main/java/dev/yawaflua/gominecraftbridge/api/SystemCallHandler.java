package dev.yawaflua.gominecraftbridge.api;

import com.google.gson.JsonElement;

@FunctionalInterface
public interface SystemCallHandler {
	JsonElement handle(SystemCallContext context, JsonElement payload) throws Exception;
}
