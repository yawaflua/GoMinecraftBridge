package dev.yawaflua.gominecraftbridge.protocol;

public record PluginLog(String stream, String level, String message, long timestampUnixMilli) {
}
