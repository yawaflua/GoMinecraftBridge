package dev.yawaflua.gominecraftbridge.management;

import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;

import java.util.List;

public record ManagedPluginSnapshot(
		PluginMetadata metadata,
		String state,
		String backend,
		String origin,
		List<PluginLog> logs
) {
	public ManagedPluginSnapshot {
		logs = logs == null ? List.of() : List.copyOf(logs);
	}
}
