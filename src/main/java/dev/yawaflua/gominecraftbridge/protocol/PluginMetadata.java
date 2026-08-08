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
		PluginEnvironment environment,
		List<ClientKeyBinding> clientKeyBindings
) {
	public PluginMetadata {
		authors = authors == null ? List.of() : List.copyOf(authors);
		clientKeyBindings = clientKeyBindings == null ? List.of() : List.copyOf(clientKeyBindings);
		// Metadata produced before the environment field was introduced is
		// server-side by definition and remains valid without an ABI bump.
		environment = environment == null ? PluginEnvironment.SERVER : environment;
	}

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
			boolean configWritable,
			PluginEnvironment environment
	) {
		this(id, name, version, description, authors, website, license, apiVersion, configSchema,
				configWritable, environment, List.of());
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
		this(id, name, version, description, authors, website, null, apiVersion, configSchema, false, PluginEnvironment.SERVER, List.of());
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
		this(id, name, version, description, authors, website, null, apiVersion, configSchema, false, environment, List.of());
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
		this(id, name, version, description, authors, website, license, apiVersion, configSchema, false, PluginEnvironment.SERVER, List.of());
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
		this(id, name, version, description, authors, website, license, apiVersion, configSchema, false, environment, List.of());
	}
}
