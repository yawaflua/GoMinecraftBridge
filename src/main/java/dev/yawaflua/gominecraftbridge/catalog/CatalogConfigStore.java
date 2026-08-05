package dev.yawaflua.gominecraftbridge.catalog;

import com.google.gson.JsonParseException;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the durable GBM catalog configuration and installed-package index. */
final class CatalogConfigStore {
	private final Path root;
	private final Path packagesRoot;
	private final Path configFile;
	private State state;

	CatalogConfigStore(Path root, String packagesDirectory) throws IOException {
		this.root = root.toAbsolutePath().normalize();
		this.packagesRoot = this.root.resolve(packagesDirectory);
		this.configFile = this.root.resolve("repository.json");
		Files.createDirectories(this.packagesRoot);
		this.state = load();
	}

	synchronized CatalogSettings settings() {
		return new CatalogSettings(this.state.backendUrl, this.state.automaticUpdates);
	}

	synchronized List<InstalledCatalogPackage> installedPackages() {
		return List.copyOf(this.state.packages.values());
	}

	synchronized InstalledCatalogPackage packageByProject(String projectId) {
		return this.state.packages.get(projectId);
	}

	synchronized InstalledCatalogPackage packageByPlugin(String pluginId) {
		if (pluginId == null || pluginId.isBlank()) {
			return null;
		}
		return this.state.packages.values().stream()
				.filter(item -> pluginId.equals(item.pluginId()))
				.findFirst()
				.orElse(null);
	}

	synchronized void saveSettings(String backendUrl, boolean automaticUpdates) throws IOException {
		String normalized = normalizeBackendUrl(backendUrl);
		new BackendCatalogClient(normalized);
		State previous = this.state;
		this.state = previous.copy();
		this.state.backendUrl = normalized;
		this.state.automaticUpdates = automaticUpdates;
		try {
			write();
		} catch (IOException exception) {
			this.state = previous;
			throw exception;
		}
	}

	synchronized void saveInstalled(InstalledCatalogPackage installed) throws IOException {
		State previous = this.state;
		this.state = previous.copy();
		this.state.packages.put(installed.projectId(), installed);
		try {
			write();
		} catch (IOException exception) {
			this.state = previous;
			throw exception;
		}
	}

	synchronized InstalledCatalogPackage associatePlugin(Path binary, String pluginId) throws IOException {
		if (binary == null || pluginId == null || pluginId.isBlank()) {
			return null;
		}
		Path normalized = binary.toAbsolutePath().normalize();
		for (InstalledCatalogPackage item : this.state.packages.values()) {
			Path directory = this.packagesRoot.resolve(item.slug()).normalize();
			if (!normalized.startsWith(directory) || pluginId.equals(item.pluginId())) {
				continue;
			}
			InstalledCatalogPackage associated = new InstalledCatalogPackage(
					item.projectId(), item.slug(), pluginId, item.version(), item.downloadUrl(),
					item.sha256(), item.binaryPath(), item.installedAtUnixMilli()
			);
			saveInstalled(associated);
			return associated;
		}
		return null;
	}

	Path root() {
		return this.root;
	}

	Path packagesRoot() {
		return this.packagesRoot;
	}

	private State load() throws IOException {
		if (!Files.isRegularFile(this.configFile)) {
			return new State();
		}
		try {
			State loaded = ProtocolJson.GSON.fromJson(Files.readString(this.configFile), State.class);
			if (loaded == null) {
				throw new IOException("GBM catalog configuration is empty");
			}
			loaded.backendUrl = normalizeBackendUrl(loaded.backendUrl);
			new BackendCatalogClient(loaded.backendUrl);
			loaded.packages = loaded.packages == null
					? new LinkedHashMap<>()
					: new LinkedHashMap<>(loaded.packages);
			return loaded;
		} catch (JsonParseException | IllegalArgumentException exception) {
			throw new IOException("Cannot read " + this.configFile + ": " + rootMessage(exception), exception);
		}
	}

	private void write() throws IOException {
		Files.createDirectories(this.root);
		Path temporary = this.root.resolve("repository.json.tmp");
		Files.writeString(temporary, ProtocolJson.GSON.toJson(this.state));
		AtomicFiles.replace(temporary, this.configFile);
	}

	private static String normalizeBackendUrl(String value) {
		String result = value == null || value.isBlank()
				? GbmCatalogService.DEFAULT_BACKEND_URL
				: value.trim();
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private static final class State {
		int formatVersion = 1;
		String backendUrl = GbmCatalogService.DEFAULT_BACKEND_URL;
		boolean automaticUpdates;
		Map<String, InstalledCatalogPackage> packages = new LinkedHashMap<>();

		State copy() {
			State copy = new State();
			copy.formatVersion = this.formatVersion;
			copy.backendUrl = this.backendUrl;
			copy.automaticUpdates = this.automaticUpdates;
			copy.packages = new LinkedHashMap<>(this.packages);
			return copy;
		}
	}
}
