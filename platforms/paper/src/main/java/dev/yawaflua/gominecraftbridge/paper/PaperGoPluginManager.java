package dev.yawaflua.gominecraftbridge.paper;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.host.NativePackageScanner;
import dev.yawaflua.gominecraftbridge.host.NativePluginRegistry;
import dev.yawaflua.gominecraftbridge.host.PluginState;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.InitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallRequest;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallResult;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PaperGoPluginManager {
	private static final int MAX_SYSTEM_CALL_CHAIN = 32;

	private final Logger logger;
	private final Path pluginDirectory;
	private final Path dataDirectory;
	private final NativePluginRegistry registry;
	private final PaperSnapshotFactory snapshots = new PaperSnapshotFactory();
	private final PaperActionExecutor actions = new PaperActionExecutor();
	private final PaperSystemCalls systemCalls = new PaperSystemCalls(this.snapshots);
	private boolean running;
	private long tick;

	PaperGoPluginManager(Logger logger, Path pluginDataDirectory) {
		this.logger = logger;
		this.pluginDirectory = pluginDataDirectory.resolve("plugins");
		this.dataDirectory = pluginDataDirectory.resolve("data");
		this.registry = new NativePluginRegistry(new NativePackageScanner(List.of(
				new NativePackageScanner.SearchRoot(this.pluginDirectory, false)
		)));
	}

	synchronized void discover() {
		scanCandidates();
	}

	synchronized ReloadResult rescan() {
		int before = this.registry.plugins().size();
		scanCandidates();
		return new ReloadResult(true, "Package check completed; new plugins: "
				+ (this.registry.plugins().size() - before));
	}

	private void scanCandidates() {
		try {
			Files.createDirectories(this.pluginDirectory);
			Files.createDirectories(this.dataDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create Paper Go plugin directories", exception);
		}

		NativePluginRegistry.DiscoveryReport report = this.registry.discover(
				metadata -> metadata.environment().supportsServer()
		);
		for (NativePluginRegistry.SkippedPlugin skipped : report.skipped()) {
			this.logger.info("Skipping client-only GBM plugin " + skipped.plugin().metadata().id());
		}
		for (NativePluginRegistry.PackageFailure failure : report.failures()) {
			this.logger.log(Level.SEVERE, "Cannot load GBM plugin " + failure.path(), failure.cause());
		}
		for (NativePackageScanner.ScanFailure failure : report.scanFailures()) {
			this.logger.log(Level.SEVERE, "Cannot scan native plugin directory " + failure.root(), failure.cause());
		}
		for (LoadedPlugin plugin : report.discovered()) {
			bridgeLog(plugin, "info", "Package check passed: " + plugin.backend().origin());
			this.logger.info("Discovered GBM plugin " + plugin.metadata().name()
					+ " " + plugin.metadata().version() + " from " + plugin.backend().origin().getFileName());
			if (this.running) {
				startPlugin(plugin);
			}
		}
	}

	synchronized void start() {
		this.running = true;
		for (LoadedPlugin plugin : this.registry.plugins()) {
			startPlugin(plugin);
		}
	}

	synchronized void tick() {
		this.tick++;
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.TICK,
					this.snapshots.create(this.tick, plugin.snapshotSubscription()));
		}
	}

	synchronized void chat(ChatEvent event) {
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.CHAT, event);
		}
	}

	synchronized void death(DeathEvent event) {
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.DEATH, event);
		}
	}

	synchronized void stop() {
		for (LoadedPlugin plugin : runningPlugins()) {
			try {
				processResponse(plugin, plugin.invoke(
						Protocol.Operation.DEINIT, new DeinitEvent("paper_plugin_disabling")
				), 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Deinit failed: " + rootMessage(exception));
				this.logger.log(Level.SEVERE, "Go plugin " + plugin.metadata().id() + " failed during deinit", exception);
			} finally {
				plugin.markStopped();
			}
		}
		this.running = false;
	}

	synchronized ReloadResult reload(String pluginId) {
		if (!this.running) {
			return new ReloadResult(false, "Paper plugin runtime is stopped");
		}
		LoadedPlugin plugin = this.registry.plugin(pluginId);
		if (plugin == null) {
			return new ReloadResult(false, "Unknown Go plugin: " + pluginId);
		}
		if (plugin.state() == PluginState.RUNNING) {
			try {
				processResponse(plugin, plugin.invoke(
						Protocol.Operation.DEINIT, new DeinitEvent("paper_admin_reload")
				), 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Reload deinit failed: " + rootMessage(exception));
			}
		}
		plugin.prepareReload();
		boolean started = startPlugin(plugin);
		return new ReloadResult(started, started
				? "Go plugin " + pluginId + " restarted (the native binary remains loaded)"
				: "Go plugin " + pluginId + " failed to restart");
	}

	synchronized BridgeManagementSnapshot managementSnapshot(String message) {
		List<ManagedPluginSnapshot> pluginSnapshots = this.registry.plugins().stream()
				.map(plugin -> new ManagedPluginSnapshot(
						plugin.metadata(), plugin.state().name().toLowerCase(Locale.ROOT),
						plugin.backend().getClass().getSimpleName(),
						plugin.backend().origin().toString(), plugin.logs()
				))
				.toList();
		return new BridgeManagementSnapshot(
				Instant.now().toEpochMilli(), this.running, this.running, message,
				this.registry.inspections(), pluginSnapshots
		);
	}

	private boolean startPlugin(LoadedPlugin plugin) {
		try {
			Path pluginData = this.dataDirectory.resolve(plugin.metadata().id());
			Files.createDirectories(pluginData);
			PluginResponse response = plugin.invoke(
					Protocol.Operation.INIT,
					new InitEvent(
							Bukkit.getMinecraftVersion(), true,
							pluginData.toAbsolutePath().toString(), PluginEnvironment.SERVER
					)
			);
			processResponse(plugin, response, 0);
			if (response.isError()) {
				plugin.disable();
				bridgeLog(plugin, "error", "Initialization failed: " + response.error());
				return false;
			}
			plugin.markRunning();
			bridgeLog(plugin, "info", "Plugin started on Paper/Purpur");
			return true;
		} catch (Exception exception) {
			disable(plugin, "initialization failed", exception);
			return false;
		}
	}

	private void invoke(LoadedPlugin plugin, Protocol.Operation operation, Object event) {
		try {
			processResponse(plugin, plugin.invoke(operation, event), 0);
		} catch (RuntimeException exception) {
			disable(plugin, operation + " failed", exception);
		}
	}

	private void processResponse(LoadedPlugin plugin, PluginResponse response, int systemCallDepth) {
		for (PluginLog log : response.logs()) {
			writeLog(plugin, log);
		}
		if (response.snapshot() != null) {
			plugin.snapshotSubscription(response.snapshot());
		}
		if (response.isPanic()) {
			disable(plugin, "callback panicked: " + response.error(), null);
			return;
		}
		if (response.isError()) {
			bridgeLog(plugin, "error", "Callback returned an error: " + response.error());
		}

		response.actions().forEach(action -> {
			try {
				this.actions.execute(action);
			} catch (RuntimeException exception) {
				this.logger.log(Level.SEVERE,
						"Action " + action.type() + " from " + plugin.metadata().id() + " failed", exception);
			}
		});

		if (response.systemCalls().isEmpty()) {
			return;
		}
		if (systemCallDepth >= MAX_SYSTEM_CALL_CHAIN) {
			disable(plugin, "system call chain exceeded " + MAX_SYSTEM_CALL_CHAIN, null);
			return;
		}
		for (SystemCallRequest request : response.systemCalls()) {
			SystemCallResult result = executeSystemCall(request);
			try {
				processResponse(plugin, plugin.invoke(Protocol.Operation.SYSTEM_CALL_RESULT, result), systemCallDepth + 1);
			} catch (RuntimeException exception) {
				disable(plugin, "system call result callback failed", exception);
				return;
			}
		}
	}

	private SystemCallResult executeSystemCall(SystemCallRequest request) {
		try {
			JsonElement result = this.systemCalls.execute(
					request.name(), request.payload() == null ? JsonNull.INSTANCE : request.payload(), this.tick
			);
			return new SystemCallResult(
					request.id(), request.name(), true, result == null ? JsonNull.INSTANCE : result, null
			);
		} catch (Exception exception) {
			return new SystemCallResult(
					request.id(), request.name(), false, JsonNull.INSTANCE, rootMessage(exception)
			);
		}
	}

	private List<LoadedPlugin> runningPlugins() {
		return this.registry.runningPlugins();
	}

	private void disable(LoadedPlugin plugin, String reason, Throwable throwable) {
		plugin.disable();
		bridgeLog(plugin, "error", "Plugin disabled: " + reason
				+ (throwable == null ? "" : " — " + rootMessage(throwable)));
		if (throwable == null) {
			this.logger.severe("Disabled Go plugin " + plugin.metadata().id() + ": " + reason);
		} else {
			this.logger.log(Level.SEVERE, "Disabled Go plugin " + plugin.metadata().id() + ": " + reason, throwable);
		}
	}

	private void writeLog(LoadedPlugin plugin, PluginLog log) {
		plugin.appendLog(log);
		String message = "[Go/" + plugin.metadata().id() + "/" + log.stream() + "] " + log.message();
		Level level = switch (log.level() == null ? "info" : log.level()) {
			case "trace", "debug" -> Level.FINE;
			case "warn" -> Level.WARNING;
			case "error" -> Level.SEVERE;
			default -> Level.INFO;
		};
		this.logger.log(level, message);
	}

	private void bridgeLog(LoadedPlugin plugin, String level, String message) {
		plugin.appendLog(new PluginLog("paper-bridge", level, message, Instant.now().toEpochMilli()));
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
