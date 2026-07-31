package dev.yawaflua.gominecraftbridge.catalog;

import com.google.gson.JsonParseException;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent catalog settings, source manifests, installation, and automatic updates. */
public final class PackageCatalog {
	public static final String DEFAULT_BACKEND_URL = "http://localhost:8080";
	public static final String SIDECAR_FILE = "gbm-package.json";

	private final Path root;
	private final Path packagesRoot;
	private final Path configFile;
	private State state;

	public PackageCatalog(Path root) throws IOException {
		this.root = root.toAbsolutePath().normalize();
		this.packagesRoot = this.root.resolve("client-plugins");
		this.configFile = this.root.resolve("repository.json");
		Files.createDirectories(this.packagesRoot);
		this.state = load();
	}

	public synchronized Settings settings() {
		return new Settings(this.state.backendUrl, this.state.automaticUpdates);
	}

	public synchronized List<InstalledCatalogPackage> installedPackages() {
		return List.copyOf(this.state.packages.values());
	}

	public synchronized Path packagesRoot() {
		return this.packagesRoot;
	}

	public synchronized void saveSettings(String backendUrl, boolean automaticUpdates) throws IOException {
		// Constructing the client performs strict URL validation before it reaches disk.
		new BackendCatalogClient(backendUrl);
		this.state.backendUrl = normalizeBackendUrl(backendUrl);
		this.state.automaticUpdates = automaticUpdates;
		writeState();
	}

	public List<CatalogProject> search(String query) throws IOException {
		return client().search(query);
	}

	public InstalledCatalogPackage install(CatalogProject project) throws IOException {
		if (project == null || project.id().isBlank() || project.slug().isBlank()) {
			throw new IllegalArgumentException("A catalog project is required");
		}
		CatalogVersion version = client().version(project.id(), "latest");
		return install(project.id(), project.slug(), version, existing(project.id()));
	}

	public List<CatalogUpdate> checkForUpdates() throws IOException {
		List<InstalledCatalogPackage> installed = installedPackages();
		return installed.isEmpty() ? List.of() : client().checkNewVersions(installed);
	}

	public CatalogUpdate checkForUpdate(String projectId) throws IOException {
		InstalledCatalogPackage installed = existing(projectId);
		if (installed == null) {
			return null;
		}
		return client().checkNewVersions(List.of(installed)).stream().findFirst().orElse(null);
	}

	public UpdateRun updateAvailablePackages() throws IOException {
		List<CatalogUpdate> updates = checkForUpdates();
		int installed = 0;
		List<String> failures = new ArrayList<>();
		for (CatalogUpdate update : updates) {
			if (!update.updateAvailable()) {
				continue;
			}
			InstalledCatalogPackage current = existing(update.projectId());
			if (current == null) {
				continue;
			}
			try {
				BackendCatalogClient backend = client();
				CatalogVersion version = backend.version(update.projectId(), update.latestVersion());
				install(update.projectId(), current.slug(), version, current, backend);
				installed++;
			} catch (IOException | RuntimeException exception) {
				failures.add(current.slug() + ": " + rootMessage(exception));
			}
		}
		return new UpdateRun(updates.size(), installed, List.copyOf(failures));
	}

	public synchronized InstalledCatalogPackage packageForPlugin(String pluginId) {
		if (pluginId == null || pluginId.isBlank()) {
			return null;
		}
		return this.state.packages.values().stream()
				.filter(item -> pluginId.equals(item.pluginId()))
				.findFirst()
				.orElse(null);
	}

	public synchronized void associatePlugin(Path binary, String pluginId) throws IOException {
		if (binary == null || pluginId == null || pluginId.isBlank()) {
			return;
		}
		Path normalized = binary.toAbsolutePath().normalize();
		boolean changed = false;
		for (var entry : List.copyOf(this.state.packages.entrySet())) {
			InstalledCatalogPackage item = entry.getValue();
			Path packageDirectory = this.packagesRoot.resolve(item.slug()).normalize();
			if (!normalized.startsWith(packageDirectory) || pluginId.equals(item.pluginId())) {
				continue;
			}
			InstalledCatalogPackage associated = new InstalledCatalogPackage(
					item.projectId(), item.slug(), pluginId, item.version(), item.downloadUrl(),
					item.sha256(), item.binaryPath(), item.installedAtUnixMilli()
			);
			this.state.packages.put(entry.getKey(), associated);
			writeSidecar(associated);
			changed = true;
		}
		if (changed) {
			writeState();
		}
	}

	public synchronized String downloadUrl(InstalledCatalogPackage item, String version) {
		return new BackendCatalogClient(this.state.backendUrl)
				.downloadUri(item.slug(), version).toString();
	}

	private InstalledCatalogPackage install(
			String projectId,
			String slug,
			CatalogVersion version,
			InstalledCatalogPackage current
	) throws IOException {
		return install(projectId, slug, version, current, client());
	}

	private InstalledCatalogPackage install(
			String projectId,
			String slug,
			CatalogVersion version,
			InstalledCatalogPackage current,
			BackendCatalogClient backend
	) throws IOException {
		if (!version.metadata().supportsClient()) {
			throw new IOException("Version " + version.version() + " is server-only and cannot be installed in the client runtime");
		}
		BackendCatalogClient.Download download = backend.download(slug, version.version());
		if (version.sizeBytes() <= 0 || version.sizeBytes() != download.data().length) {
			throw new IOException("Downloaded package size does not match backend metadata");
		}
		if (!version.sha256().matches("(?i)[0-9a-f]{64}")) {
			throw new IOException("Backend version does not contain a valid SHA-256 digest");
		}
		String actualHash = sha256(download.data());
		if (!actualHash.equalsIgnoreCase(version.sha256())) {
			throw new IOException("Downloaded package SHA-256 does not match backend metadata");
		}
		Path binary = PackageInstaller.install(this.packagesRoot, slug, download.data(), download.contentType());
		InstalledCatalogPackage installed = new InstalledCatalogPackage(
				projectId,
				slug,
				current == null ? "" : current.pluginId(),
				version.version(),
				download.uri().toString(),
				actualHash,
				this.root.relativize(binary.toAbsolutePath().normalize()).toString(),
				Instant.now().toEpochMilli()
		);
		synchronized (this) {
			this.state.packages.put(projectId, installed);
			writeSidecar(installed);
			writeState();
		}
		return installed;
	}

	private synchronized InstalledCatalogPackage existing(String projectId) {
		return this.state.packages.get(projectId);
	}

	private synchronized BackendCatalogClient client() {
		return new BackendCatalogClient(this.state.backendUrl);
	}

	private State load() throws IOException {
		if (!Files.isRegularFile(this.configFile)) {
			return new State();
		}
		try {
			State loaded = ProtocolJson.GSON.fromJson(Files.readString(this.configFile), State.class);
			if (loaded == null) {
				throw new IOException("Catalog configuration is empty");
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

	private void writeState() throws IOException {
		Files.createDirectories(this.root);
		Path temporary = this.root.resolve("repository.json.tmp");
		Files.writeString(temporary, ProtocolJson.GSON.toJson(this.state));
		atomicReplace(temporary, this.configFile);
	}

	private void writeSidecar(InstalledCatalogPackage installed) throws IOException {
		Path directory = this.packagesRoot.resolve(installed.slug()).normalize();
		if (!directory.startsWith(this.packagesRoot) || !Files.isDirectory(directory)) {
			throw new IOException("Installed package directory is missing");
		}
		Path temporary = directory.resolve(SIDECAR_FILE + ".tmp");
		Files.writeString(temporary, ProtocolJson.GSON.toJson(installed));
		atomicReplace(temporary, directory.resolve(SIDECAR_FILE));
	}

	private static void atomicReplace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String normalizeBackendUrl(String value) {
		String result = value == null || value.isBlank() ? DEFAULT_BACKEND_URL : value.trim();
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	public record Settings(String backendUrl, boolean automaticUpdates) {
	}

	public record UpdateRun(int checked, int installed, List<String> failures) {
		public UpdateRun {
			failures = failures == null ? List.of() : List.copyOf(failures);
		}
	}

	private static final class State {
		int formatVersion = 1;
		String backendUrl = DEFAULT_BACKEND_URL;
		boolean automaticUpdates;
		Map<String, InstalledCatalogPackage> packages = new LinkedHashMap<>();
	}
}
