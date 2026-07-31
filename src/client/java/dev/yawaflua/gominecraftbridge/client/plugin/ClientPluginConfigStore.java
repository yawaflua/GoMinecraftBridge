package dev.yawaflua.gominecraftbridge.client.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Filesystem boundary for client plugin data and saved JSON configurations. */
public final class ClientPluginConfigStore {
	private final Path dataRoot;

	public ClientPluginConfigStore(Path dataRoot) {
		this.dataRoot = dataRoot;
	}

	public void initialize() throws IOException {
		Files.createDirectories(this.dataRoot);
	}

	public Path dataDirectory(String pluginId) throws IOException {
		Path directory = this.dataRoot.resolve(pluginId);
		Files.createDirectories(directory);
		return directory;
	}

	public JsonObject read(String pluginId) throws IOException {
		Path path = this.dataRoot.resolve(pluginId).resolve("config.json");
		if (!Files.isRegularFile(path)) {
			return null;
		}
		JsonElement saved = ProtocolJson.GSON.fromJson(Files.readString(path), JsonElement.class);
		if (saved == null || !saved.isJsonObject()) {
			throw new IOException("Saved plugin configuration is not a JSON object");
		}
		return saved.getAsJsonObject();
	}

	public void write(String pluginId, JsonObject config) throws IOException {
		Path directory = dataDirectory(pluginId);
		Path target = directory.resolve("config.json");
		Path temporary = directory.resolve("config.json.tmp");
		Files.writeString(temporary, ProtocolJson.GSON.toJson(config));
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
