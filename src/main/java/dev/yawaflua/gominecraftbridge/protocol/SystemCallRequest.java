package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonElement;

public record SystemCallRequest(String id, String name, JsonElement payload) {
}
