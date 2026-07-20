package dev.yawaflua.gominecraftbridge.api;

import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GoPluginRegistry {
	private final Map<String, PluginMetadata> plugins = new LinkedHashMap<>();

	public synchronized void register(PluginMetadata metadata) {
		if (this.plugins.putIfAbsent(metadata.id(), metadata) != null) {
			throw new IllegalArgumentException("Plugin metadata is already registered: " + metadata.id());
		}
	}

	public synchronized Optional<PluginMetadata> find(String id) {
		return Optional.ofNullable(this.plugins.get(id));
	}

	public synchronized List<PluginMetadata> all() {
		return List.copyOf(this.plugins.values());
	}
}
