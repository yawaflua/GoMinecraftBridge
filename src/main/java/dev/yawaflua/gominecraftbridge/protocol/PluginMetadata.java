package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public record PluginMetadata(
		String id,
		String name,
		String version,
		String description,
		List<String> authors,
		String website,
		@SerializedName(value = "license", alternate = "licence") String license,
		int apiVersion,
		JsonObject configSchema,
		boolean configWritable,
		PluginEnvironment environment
) {
	public PluginMetadata {
		authors = authors == null ? List.of() : List.copyOf(authors);
		// Metadata produced before the environment field was introduced is
		// server-side by definition and remains valid without an ABI bump.
		environment = environment == null ? PluginEnvironment.SERVER : environment;
	}

	/** Source-compatible constructor for metadata created before license and environment were introduced. */
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
		this(id, name, version, description, authors, website, null, apiVersion, configSchema, false, PluginEnvironment.SERVER);
	}

	/** Source-compatible constructor for metadata created before license and writable configs. */
	public PluginMetadata(
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
		this(id, name, version, description, authors, website, null, apiVersion, configSchema, false, environment);
	}

	/** Convenience constructor for licensed metadata without writable configs or a declared environment. */
	public PluginMetadata(
			String id,
			String name,
			String version,
			String description,
			List<String> authors,
			String website,
			String license,
			int apiVersion,
			JsonObject configSchema
	) {
		this(id, name, version, description, authors, website, license, apiVersion, configSchema, false, PluginEnvironment.SERVER);
	}

	/** Convenience constructor for licensed metadata without writable configs. */
	public PluginMetadata(
			String id,
			String name,
			String version,
			String description,
			List<String> authors,
			String website,
			String license,
			int apiVersion,
			JsonObject configSchema,
			PluginEnvironment environment
	) {
		this(id, name, version, description, authors, website, license, apiVersion, configSchema, false, environment);
	}
}
