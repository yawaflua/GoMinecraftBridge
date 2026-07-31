package dev.yawaflua.gominecraftbridge.client.catalog;

import dev.yawaflua.gominecraftbridge.catalog.CatalogProject;
import dev.yawaflua.gominecraftbridge.catalog.CatalogSettings;
import dev.yawaflua.gominecraftbridge.catalog.CatalogUpdateRun;
import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.catalog.InstalledCatalogPackage;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Owns asynchronous catalog commands and their UI-facing state. */
public final class CatalogTaskController implements AutoCloseable {
	private final Logger logger;
	private final Consumer<Runnable> clientExecutor;
	private final Runnable catalogChanged;
	private final Runnable packagesInstalled;
	private final Runnable stateChanged;
	private final ExecutorService worker;
	private final GbmCatalogService catalog;
	private volatile List<CatalogProject> searchResults = List.of();
	private volatile String status;

	public CatalogTaskController(
			Path configDirectory,
			Logger logger,
			Consumer<Runnable> clientExecutor,
			Runnable catalogChanged,
			Runnable packagesInstalled,
			Runnable stateChanged
	) {
		this.logger = logger;
		this.clientExecutor = clientExecutor;
		this.catalogChanged = catalogChanged;
		this.packagesInstalled = packagesInstalled;
		this.stateChanged = stateChanged;
		this.worker = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "gbm-catalog");
			thread.setDaemon(true);
			return thread;
		});

		GbmCatalogService initialized = null;
		String initialStatus = "GBM catalog is ready.";
		try {
			initialized = new GbmCatalogService(configDirectory.resolve("gbm"));
		} catch (IOException | RuntimeException exception) {
			initialStatus = "Cannot initialize GBM catalog: " + rootMessage(exception);
			this.logger.error("Cannot initialize GBM catalog", exception);
		}
		this.catalog = initialized;
		this.status = initialStatus;
	}

	public CatalogSettings settings() {
		return this.catalog == null
				? new CatalogSettings(GbmCatalogService.DEFAULT_BACKEND_URL, false)
				: this.catalog.settings();
	}

	public List<CatalogProject> searchResults() {
		return this.searchResults;
	}

	public List<InstalledCatalogPackage> installedPackages() {
		return this.catalog == null ? List.of() : this.catalog.installedPackages();
	}

	public String status() {
		return this.status;
	}

	public GbmCatalogService service() {
		return this.catalog;
	}

	public void saveSettings(String backendUrl, boolean automaticUpdates) {
		if (!available()) {
			return;
		}
		boolean wasEnabled = this.catalog.settings().automaticUpdates();
		try {
			this.catalog.saveSettings(backendUrl, automaticUpdates);
			this.status = "GBM catalog settings saved.";
			this.catalogChanged.run();
			if (automaticUpdates && !wasEnabled) {
				installAutomaticUpdates();
			}
		} catch (IOException | RuntimeException exception) {
			this.status = "Cannot save GBM catalog settings: " + rootMessage(exception);
		}
		this.stateChanged.run();
	}

	public void search(String query) {
		if (!available()) {
			return;
		}
		this.status = "Searching the GBM catalog…";
		this.stateChanged.run();
		this.worker.execute(() -> {
			try {
				this.searchResults = this.catalog.search(query);
				this.status = "Found " + this.searchResults.size() + " published package(s).";
			} catch (IOException | RuntimeException exception) {
				this.status = "GBM catalog search failed: " + rootMessage(exception);
			}
			notifyOnClientThread();
		});
	}

	public void install(String projectId) {
		if (!available()) {
			return;
		}
		CatalogProject project = this.searchResults.stream()
				.filter(item -> item.id().equals(projectId))
				.findFirst()
				.orElse(null);
		if (project == null) {
			this.status = "The selected GBM project is no longer available.";
			this.stateChanged.run();
			return;
		}
		this.status = "Downloading " + project.name() + "…";
		this.stateChanged.run();
		this.worker.execute(() -> {
			try {
				InstalledCatalogPackage installed = this.catalog.install(project);
				this.status = "Installed " + project.name() + " " + installed.version()
						+ ". Restart Minecraft to replace an already loaded library.";
				notifyPackagesChanged();
			} catch (IOException | RuntimeException exception) {
				this.status = "Cannot install " + project.name() + ": " + rootMessage(exception);
				notifyOnClientThread();
			}
		});
	}

	public void installAutomaticUpdates() {
		if (!available() || !this.catalog.settings().automaticUpdates()) {
			return;
		}
		this.status = "Checking GBM packages for updates…";
		this.stateChanged.run();
		this.worker.execute(() -> {
			try {
				CatalogUpdateRun result = this.catalog.installAvailableUpdates();
				this.status = result.failures().isEmpty()
						? "Checked " + result.checked() + " package(s); installed " + result.installed() + " update(s)."
						: "Installed " + result.installed() + " update(s); failures: "
								+ String.join("; ", result.failures());
				notifyPackagesChanged();
			} catch (IOException | RuntimeException exception) {
				this.status = "Automatic GBM update failed: " + rootMessage(exception);
				notifyOnClientThread();
			}
		});
	}

	private boolean available() {
		if (this.catalog != null) {
			return true;
		}
		this.status = "GBM catalog is unavailable; check repository.json.";
		this.stateChanged.run();
		return false;
	}

	private void notifyPackagesChanged() {
		this.clientExecutor.accept(() -> {
			this.packagesInstalled.run();
			this.stateChanged.run();
		});
	}

	private void notifyOnClientThread() {
		this.clientExecutor.accept(this.stateChanged);
	}

	@Override
	public void close() {
		this.worker.shutdownNow();
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
