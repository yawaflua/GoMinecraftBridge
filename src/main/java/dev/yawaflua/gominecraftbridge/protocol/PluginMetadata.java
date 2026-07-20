package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonObject;

import java.util.List;

public record PluginMetadata(
		String id,
		String name,
		String version,
		String description,
		List<String> authors,
		String website,
		int apiVersion,
		JsonObject configSchema
) {
	public PluginMetadata {
		authors = authors == null ? List.of() : List.copyOf(authors);
	}
}
