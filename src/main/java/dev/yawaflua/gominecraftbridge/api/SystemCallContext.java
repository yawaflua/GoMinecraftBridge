package dev.yawaflua.gominecraftbridge.api;

import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import net.minecraft.server.MinecraftServer;

public record SystemCallContext(MinecraftServer server, PluginMetadata plugin) {
}
