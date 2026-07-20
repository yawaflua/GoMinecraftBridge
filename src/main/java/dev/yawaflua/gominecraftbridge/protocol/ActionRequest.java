package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonObject;

public record ActionRequest(String type, JsonObject payload) {
}
