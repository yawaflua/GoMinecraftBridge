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
		JsonObject configSchema,
		PluginEnvironment environment
) {
	public PluginMetadata {
		authors = authors == null ? List.of() : List.copyOf(authors);
		// Metadata produced before the environment field was introduced is
		// server-side by definition and remains valid without an ABI bump.
		environment = environment == null ? PluginEnvironment.SERVER : environment;
	}

	/** Source-compatible constructor for metadata created before environment was introduced. */
	public PluginMetadata(
			String id,
			String name,
			String version,
			String description,
			List<String> authors,
			String website,
			int apiVersion,
			JsonObject configSchema
	) {
		this(id, name, version, description, authors, website, apiVersion, configSchema, PluginEnvironment.SERVER);
	}
}
