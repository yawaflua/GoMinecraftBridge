package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonElement;

public record SystemCallResult(String id, String name, boolean success, JsonElement data, String error) {
}
