package dev.yawaflua.gominecraftbridge.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.yawaflua.gominecraftbridge.api.GoMinecraftBridgeApi;
import dev.yawaflua.gominecraftbridge.api.SystemCallContext;
import dev.yawaflua.gominecraftbridge.api.SystemCallHandler;
import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.InitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallRequest;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;

public final class GoPluginManager {
	private static final int MAX_SYSTEM_CALL_CHAIN = 32;

	private final Logger logger;
	private final Path pluginDirectory;
	private final Path dataDirectory;
	private final MinecraftSnapshotFactory snapshots = new MinecraftSnapshotFactory();
	private final ActionExecutor actions = new ActionExecutor();
	private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();
	private final List<PackageInspection> packageInspections = new ArrayList<>();
	/** Lifecycle flag is read by the networking/admin thread as well as the server thread. */
	private volatile boolean serverRunning;

	public GoPluginManager(Logger logger) {
		this.logger = logger;
		Path root = FabricLoader.getInstance().getConfigDir().resolve("go-minecraft-bridge");
		this.pluginDirectory = root.resolve("plugins");
		this.dataDirectory = root.resolve("data");
	}

	public synchronized void discover() {
		scanCandidates(null);
	}

	public synchronized ReloadResult rescan(MinecraftServer server) {
		int before = this.plugins.size();
		scanCandidates(server);
		int added = this.plugins.size() - before;
		return new ReloadResult(true, "Package check completed; new plugins: " + added);
	}

	private void scanCandidates(MinecraftServer server) {
		this.packageInspections.clear();
		try {
			Files.createDirectories(this.pluginDirectory);
			Files.createDirectories(this.dataDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create Go plugin directories", exception);
		}

		for (Path candidate : nativeCandidates()) {
			try {
				Path normalized = candidate.toAbsolutePath().normalize();
				LoadedPlugin alreadyLoaded = this.plugins.values().stream()
						.filter(plugin -> plugin.backend().origin().equals(normalized))
						.findFirst()
						.orElse(null);
				if (alreadyLoaded != null) {
					this.packageInspections.add(new PackageInspection(
							normalized.toString(), true, alreadyLoaded.metadata().id(), null
					));
					continue;
				}

				LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(candidate));
				if (!plugin.metadata().environment().supportsServer()) {
					this.packageInspections.add(new PackageInspection(
							normalized.toString(), true, plugin.metadata().id(), null
					));
					this.logger.info(
							"Skipping client-only Go plugin {} in the server runtime",
							plugin.metadata().id()
					);
					continue;
				}
				LoadedPlugin duplicate = this.plugins.putIfAbsent(plugin.metadata().id(), plugin);
				if (duplicate != null) {
					throw new IllegalArgumentException("Duplicate plugin id " + plugin.metadata().id());
				}
				GoMinecraftBridgeApi.plugins().register(plugin.metadata());
				this.packageInspections.add(new PackageInspection(
						candidate.toAbsolutePath().normalize().toString(),
						true,
						plugin.metadata().id(),
						null
				));
				bridgeLog(plugin, "info", "Package check passed: " + candidate.toAbsolutePath().normalize());
				this.logger.info(
						"Discovered Go plugin {} {} from {}",
						plugin.metadata().name(),
						plugin.metadata().version(),
						candidate.getFileName()
				);
				if (server != null && this.serverRunning) {
					startPlugin(plugin, server);
				}
			} catch (RuntimeException exception) {
				this.packageInspections.add(new PackageInspection(
						candidate.toAbsolutePath().normalize().toString(),
						false,
						null,
						rootMessage(exception)
				));
				this.logger.error("Cannot load Go plugin {}", candidate, exception);
			}
		}
	}

	public synchronized void start(MinecraftServer server) {
		this.serverRunning = true;
		for (LoadedPlugin plugin : this.plugins.values()) {
			startPlugin(plugin, server);
		}
	}

	public synchronized void tick(MinecraftServer server) {
		for (LoadedPlugin plugin : runningPlugins()) {
			ServerSnapshot snapshot = this.snapshots.create(server, plugin.snapshotSubscription());
			invoke(plugin, Protocol.Operation.TICK, snapshot, server);
		}
	}

	public synchronized void chat(ChatEvent event, MinecraftServer server) {
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.CHAT, event, server);
		}
	}

	public synchronized void death(DeathEvent event, MinecraftServer server) {
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.DEATH, event, server);
		}
	}

	public synchronized void stop(MinecraftServer server) {
		for (LoadedPlugin plugin : runningPlugins()) {
			try {
				PluginResponse response = plugin.invoke(
						Protocol.Operation.DEINIT,
						new DeinitEvent("server_stopping")
				);
				processResponse(plugin, response, server, 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Deinit failed: " + rootMessage(exception));
				this.logger.error("Go plugin {} failed during deinit", plugin.metadata().id(), exception);
			} finally {
				plugin.markStopped();
			}
		}
		this.serverRunning = false;
	}

	public synchronized List<LoadedPlugin> plugins() {
		return List.copyOf(this.plugins.values());
	}

	/** Returns the latest package validation results as an immutable snapshot. */
	public synchronized List<PackageInspection> packageInspections() {
		return List.copyOf(this.packageInspections);
	}

	/** Returns a point-in-time copy of a plugin's retained logs, or an empty list when unknown. */
	public synchronized List<PluginLog> pluginLogs(String pluginId) {
		LoadedPlugin plugin = this.plugins.get(pluginId);
		return plugin == null ? List.of() : plugin.logs();
	}

	public synchronized BridgeManagementSnapshot managementSnapshot(boolean canReload, String message) {
		List<ManagedPluginSnapshot> pluginSnapshots = this.plugins.values().stream()
				.map(plugin -> new ManagedPluginSnapshot(
						plugin.metadata(),
						plugin.state().name().toLowerCase(Locale.ROOT),
						plugin.backend().getClass().getSimpleName(),
						plugin.backend().origin().toString(),
						plugin.logs()
				))
				.toList();
		return new BridgeManagementSnapshot(
				Instant.now().toEpochMilli(),
				this.serverRunning,
				canReload && this.serverRunning,
				message,
				List.copyOf(this.packageInspections),
				pluginSnapshots
		);
	}

	public synchronized ReloadResult reload(String pluginId, MinecraftServer server) {
		if (!this.serverRunning) {
			return new ReloadResult(false, "Server is not running");
		}
		LoadedPlugin plugin = this.plugins.get(pluginId);
		if (plugin == null) {
			return new ReloadResult(false, "Unknown plugin: " + pluginId);
		}

		if (plugin.state() == PluginState.RUNNING) {
			try {
				PluginResponse response = plugin.invoke(
						Protocol.Operation.DEINIT,
						new DeinitEvent("admin_reload")
				);
				processResponse(plugin, response, server, 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Reload deinit failed: " + rootMessage(exception));
			}
		}

		plugin.prepareReload();
		bridgeLog(plugin, "info", "Lifecycle reload requested");
		boolean started = startPlugin(plugin, server);
		String message = started
				? "Plugin " + pluginId + " restarted (the native binary remains loaded)"
				: "Plugin " + pluginId + " failed to restart";
		return new ReloadResult(started, message);
	}

	private boolean startPlugin(LoadedPlugin plugin, MinecraftServer server) {
		try {
			Path pluginData = this.dataDirectory.resolve(plugin.metadata().id());
			Files.createDirectories(pluginData);
			PluginResponse response = plugin.invoke(
					Protocol.Operation.INIT,
					new InitEvent(
							MinecraftVersionAdapter.gameVersion(),
							server.isDedicatedServer(),
							pluginData.toAbsolutePath().toString()
					)
			);
			processResponse(plugin, response, server, 0);
			if (response.isError()) {
				plugin.disable();
				bridgeLog(plugin, "error", "Initialization failed: " + response.error());
				this.logger.error("Go plugin {} failed to initialize: {}", plugin.metadata().id(), response.error());
				return false;
			}
			plugin.markRunning();
			bridgeLog(plugin, "info", "Plugin started");
			this.logger.info("Started Go plugin {}", plugin.metadata().id());
			return true;
		} catch (Exception exception) {
			disable(plugin, "initialization failed", exception);
			return false;
		}
	}

	private void invoke(LoadedPlugin plugin, Protocol.Operation operation, Object event, MinecraftServer server) {
		try {
			PluginResponse response = plugin.invoke(operation, event);
			processResponse(plugin, response, server, 0);
		} catch (RuntimeException exception) {
			disable(plugin, operation + " failed", exception);
		}
	}

	private void processResponse(
			LoadedPlugin plugin,
			PluginResponse response,
			MinecraftServer server,
			int systemCallDepth
	) {
		for (PluginLog log : response.logs()) {
			writeLog(plugin, log);
		}

		if (response.snapshot() != null) {
			plugin.snapshotSubscription(response.snapshot());
		}

		if (response.isPanic()) {
			plugin.disable();
			this.logger.error(
					"Go plugin {} panicked: {}\n{}",
					plugin.metadata().id(),
					response.error(),
					response.stack()
			);
			return;
		}
		if (response.isError()) {
			this.logger.error("Go plugin {} returned an error: {}", plugin.metadata().id(), response.error());
		}

		response.actions().forEach(action -> {
			try {
				this.actions.execute(server, action);
			} catch (RuntimeException exception) {
				this.logger.error("Action {} from plugin {} failed", action.type(), plugin.metadata().id(), exception);
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
			SystemCallResult result = executeSystemCall(plugin, request, server);
			try {
				PluginResponse nested = plugin.invoke(Protocol.Operation.SYSTEM_CALL_RESULT, result);
				processResponse(plugin, nested, server, systemCallDepth + 1);
			} catch (RuntimeException exception) {
				disable(plugin, "system call result callback failed", exception);
				return;
			}
		}
	}

	private SystemCallResult executeSystemCall(
			LoadedPlugin plugin,
			SystemCallRequest request,
			MinecraftServer server
	) {
		SystemCallHandler handler = GoMinecraftBridgeApi.systemCalls().find(request.name()).orElse(null);
		if (handler == null) {
			return new SystemCallResult(
					request.id(),
					request.name(),
					false,
					JsonNull.INSTANCE,
					"Unknown system call " + request.name()
			);
		}

		try {
			JsonElement result = handler.handle(
					new SystemCallContext(server, plugin.metadata()),
					request.payload() == null ? JsonNull.INSTANCE : request.payload()
			);
			return new SystemCallResult(
					request.id(),
					request.name(),
					true,
					result == null ? JsonNull.INSTANCE : result,
					null
			);
		} catch (Exception exception) {
			return new SystemCallResult(
					request.id(),
					request.name(),
					false,
					JsonNull.INSTANCE,
					exception.getMessage()
			);
		}
	}

	private List<LoadedPlugin> runningPlugins() {
		return this.plugins.values().stream()
				.filter(plugin -> plugin.state() == PluginState.RUNNING)
				.toList();
	}

	private List<Path> nativeCandidates() {
		String extension = nativeExtension();
		List<Path> result = new ArrayList<>();
		List<Path> directories = List.of(
				this.pluginDirectory,
				FabricLoader.getInstance().getGameDir().resolve("mods")
		);

		for (Path directory : directories) {
			if (!Files.isDirectory(directory)) {
				continue;
			}
			try (var files = Files.list(directory)) {
				files
						.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
						.forEach(result::add);
			} catch (IOException exception) {
				this.logger.error("Cannot scan {}", directory, exception);
			}
		}

		result.sort(Comparator.comparing(Path::toString));
		return List.copyOf(result);
	}

	private static String nativeExtension() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return ".dll";
		}
		if (os.contains("mac")) {
			return ".dylib";
		}
		return ".so";
	}

	private void disable(LoadedPlugin plugin, String reason, Throwable throwable) {
		plugin.disable();
		bridgeLog(plugin, "error", "Plugin disabled: " + reason + (throwable == null ? "" : " — " + rootMessage(throwable)));
		if (throwable == null) {
			this.logger.error("Disabled Go plugin {}: {}", plugin.metadata().id(), reason);
		} else {
			this.logger.error("Disabled Go plugin {}: {}", plugin.metadata().id(), reason, throwable);
		}
	}

	private void writeLog(LoadedPlugin plugin, PluginLog log) {
		plugin.appendLog(log);
		String message = "[Go/" + plugin.metadata().id() + "/" + log.stream() + "] " + log.message();
		switch (log.level() == null ? "info" : log.level()) {
			case "trace" -> this.logger.trace(message);
			case "debug" -> this.logger.debug(message);
			case "warn" -> this.logger.warn(message);
			case "error" -> this.logger.error(message);
			default -> this.logger.info(message);
		}
	}

	private void bridgeLog(LoadedPlugin plugin, String level, String message) {
		plugin.appendLog(new PluginLog("bridge", level, message, Instant.now().toEpochMilli()));
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
