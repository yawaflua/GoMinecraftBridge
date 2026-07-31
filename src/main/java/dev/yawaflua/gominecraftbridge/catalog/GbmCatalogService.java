package dev.yawaflua.gominecraftbridge.catalog;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Application service for GBM search, installation, manifests, and updates. */
public final class GbmCatalogService {
	public static final String DEFAULT_BACKEND_URL = "http://localhost:8080";
	public static final String SIDECAR_FILE = PackageManifestStore.FILE_NAME;

	private final CatalogConfigStore config;
	private final PackageManifestStore manifests;
	private final PackageIntegrityVerifier integrity = new PackageIntegrityVerifier();

	public GbmCatalogService(Path root) throws IOException {
		this.config = new CatalogConfigStore(root);
		this.manifests = new PackageManifestStore(this.config.packagesRoot());
	}

	public CatalogSettings settings() {
		return this.config.settings();
	}

	public List<InstalledCatalogPackage> installedPackages() {
		return this.config.installedPackages();
	}

	public void saveSettings(String backendUrl, boolean automaticUpdates) throws IOException {
		this.config.saveSettings(backendUrl, automaticUpdates);
	}

	public List<CatalogProject> search(String query) throws IOException {
		return api().search(query);
	}

	public InstalledCatalogPackage install(CatalogProject project) throws IOException {
		if (project == null || project.id().isBlank() || project.slug().isBlank()) {
			throw new IllegalArgumentException("A catalog project is required");
		}
		CatalogApi backend = api();
		CatalogVersion version = backend.version(project.id(), "latest");
		return install(project.id(), project.slug(), version, packageByProject(project.id()), backend);
	}

	public List<CatalogUpdate> checkForUpdates() throws IOException {
		List<InstalledCatalogPackage> installed = installedPackages();
		return installed.isEmpty() ? List.of() : api().checkNewVersions(installed);
	}

	public CatalogUpdate checkForUpdate(String projectId) throws IOException {
		InstalledCatalogPackage installed = packageByProject(projectId);
		if (installed == null) {
			return null;
		}
		return api().checkNewVersions(List.of(installed)).stream().findFirst().orElse(null);
	}

	public CatalogUpdateRun installAvailableUpdates() throws IOException {
		List<CatalogUpdate> updates = checkForUpdates();
		int installedCount = 0;
		List<String> failures = new ArrayList<>();
		for (CatalogUpdate update : updates) {
			if (!update.updateAvailable()) {
				continue;
			}
			InstalledCatalogPackage current = packageByProject(update.projectId());
			if (current == null) {
				continue;
			}
			try {
				CatalogApi backend = api();
				CatalogVersion version = backend.version(update.projectId(), update.latestVersion());
				install(update.projectId(), current.slug(), version, current, backend);
				installedCount++;
			} catch (IOException | RuntimeException exception) {
				failures.add(current.slug() + ": " + rootMessage(exception));
			}
		}
		return new CatalogUpdateRun(updates.size(), installedCount, failures);
	}

	public InstalledCatalogPackage packageByPlugin(String pluginId) {
		return this.config.packageByPlugin(pluginId);
	}

	public void associatePlugin(Path binary, String pluginId) throws IOException {
		InstalledCatalogPackage associated = this.config.associatePlugin(binary, pluginId);
		if (associated != null) {
			this.manifests.save(associated);
		}
	}

	public String downloadUrl(InstalledCatalogPackage installed, String version) {
		return api().downloadUri(installed.slug(), version).toString();
	}

	private InstalledCatalogPackage install(
			String projectId,
			String slug,
			CatalogVersion version,
			InstalledCatalogPackage current,
			CatalogApi backend
	) throws IOException {
		if (!version.metadata().supportsClient()) {
			throw new IOException("Version " + version.version() + " is server-only and cannot be installed in the client runtime");
		}
		CatalogApi.Download download = backend.download(slug, version.version());
		String sha256 = this.integrity.verify(version, download.data());
		Path binary = PackageInstaller.install(
				this.config.packagesRoot(), slug, download.data(), download.contentType()
		);
		InstalledCatalogPackage installed = new InstalledCatalogPackage(
				projectId,
				slug,
				current == null ? "" : current.pluginId(),
				version.version(),
				download.uri().toString(),
				sha256,
				this.config.root().relativize(binary.toAbsolutePath().normalize()).toString(),
				Instant.now().toEpochMilli()
		);
		this.manifests.save(installed);
		this.config.saveInstalled(installed);
		return installed;
	}

	private InstalledCatalogPackage packageByProject(String projectId) {
		return this.config.packageByProject(projectId);
	}

	private CatalogApi api() {
		return new BackendCatalogClient(settings().backendUrl());
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
