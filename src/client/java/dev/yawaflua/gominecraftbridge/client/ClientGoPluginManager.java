package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.host.PluginState;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ActionRequest;
import dev.yawaflua.gominecraftbridge.protocol.ClientTickEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.InitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallRequest;
import dev.yawaflua.gominecraftbridge.protocol.SystemCallResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-process native host. It intentionally has its own package and data
 * directories and never delegates actions or system calls to a remote server.
 */
public final class ClientGoPluginManager {
	private static final int MAX_SYSTEM_CALL_CHAIN = 32;

	private final Logger logger;
	private final Path pluginDirectory;
	private final Path dataDirectory;
	private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();
	private final List<PackageInspection> packageInspections = new ArrayList<>();
	private long tick;
	private boolean running;

	public ClientGoPluginManager(Logger logger) {
		this.logger = logger;
		Path root = FabricLoader.getInstance().getConfigDir().resolve("go-minecraft-bridge");
		this.pluginDirectory = root.resolve("client-plugins");
		this.dataDirectory = root.resolve("client-data");
	}

	public synchronized void discover() {
		this.packageInspections.clear();
		try {
			Files.createDirectories(this.pluginDirectory);
			Files.createDirectories(this.dataDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create client Go plugin directories", exception);
		}

		for (Path candidate : nativeCandidates()) {
			Path normalized = candidate.toAbsolutePath().normalize();
			try {
				LoadedPlugin existing = this.plugins.values().stream()
						.filter(plugin -> plugin.backend().origin().equals(normalized))
						.findFirst()
						.orElse(null);
				if (existing != null) {
					this.packageInspections.add(new PackageInspection(
							normalized.toString(), true, existing.metadata().id(), null
					));
					continue;
				}

				LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(candidate));
				if (!plugin.metadata().environment().supportsClient()) {
					this.packageInspections.add(new PackageInspection(
							normalized.toString(), true, plugin.metadata().id(), null
					));
					this.logger.info(
							"Skipping server-only Go plugin {} in the client runtime",
							plugin.metadata().id()
					);
					continue;
				}
				if (this.plugins.putIfAbsent(plugin.metadata().id(), plugin) != null) {
					throw new IllegalArgumentException("Duplicate client plugin id " + plugin.metadata().id());
				}
				this.packageInspections.add(new PackageInspection(
						normalized.toString(), true, plugin.metadata().id(), null
				));
				bridgeLog(plugin, "info", "Client package check passed: " + normalized);
				this.logger.info(
						"Discovered client Go plugin {} {} from {}",
						plugin.metadata().name(), plugin.metadata().version(), candidate.getFileName()
				);
				if (this.running) {
					startPlugin(plugin);
				}
			} catch (RuntimeException exception) {
				this.packageInspections.add(new PackageInspection(
						normalized.toString(), false, null, rootMessage(exception)
				));
				this.logger.error("Cannot load client Go plugin {}", candidate, exception);
			}
		}
	}

	public synchronized void start(Minecraft client) {
		this.running = true;
		for (LoadedPlugin plugin : this.plugins.values()) {
			startPlugin(plugin);
		}
	}

	public synchronized void tick(Minecraft client) {
		this.tick++;
		for (LoadedPlugin plugin : runningPlugins()) {
			invoke(plugin, Protocol.Operation.CLIENT_TICK, createTick(client), client);
		}
	}

	public synchronized void stop(Minecraft client) {
		for (LoadedPlugin plugin : runningPlugins()) {
			try {
				PluginResponse response = plugin.invoke(
						Protocol.Operation.DEINIT,
						new DeinitEvent("client_stopping")
				);
				processResponse(plugin, response, client, 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Client deinit failed: " + rootMessage(exception));
				this.logger.error("Client Go plugin {} failed during deinit", plugin.metadata().id(), exception);
			} finally {
				plugin.markStopped();
			}
		}
		this.running = false;
	}

	public synchronized ReloadResult rescan() {
		int before = this.plugins.size();
		discover();
		return new ReloadResult(true, "Client package check completed; new plugins: " + (this.plugins.size() - before));
	}

	public synchronized ReloadResult reload(String pluginId, Minecraft client) {
		LoadedPlugin plugin = this.plugins.get(pluginId);
		if (plugin == null) {
			return new ReloadResult(false, "Unknown client plugin: " + pluginId);
		}
		if (plugin.state() == PluginState.RUNNING) {
			try {
				processResponse(plugin, plugin.invoke(
						Protocol.Operation.DEINIT, new DeinitEvent("client_admin_reload")
				), client, 0);
			} catch (RuntimeException exception) {
				bridgeLog(plugin, "error", "Client reload deinit failed: " + rootMessage(exception));
			}
		}
		plugin.prepareReload();
		boolean started = startPlugin(plugin);
		return new ReloadResult(started, started
				? "Client plugin " + pluginId + " restarted (the native binary remains loaded)"
				: "Client plugin " + pluginId + " failed to restart");
	}

	public synchronized BridgeManagementSnapshot managementSnapshot(String message) {
		List<ManagedPluginSnapshot> snapshots = this.plugins.values().stream()
				.map(plugin -> new ManagedPluginSnapshot(
						plugin.metadata(),
						plugin.state().name().toLowerCase(Locale.ROOT),
						plugin.backend().getClass().getSimpleName(),
						plugin.backend().origin().toString(),
						plugin.logs()
				))
				.toList();
		return new BridgeManagementSnapshot(
				Instant.now().toEpochMilli(), this.running, this.running, message,
				List.copyOf(this.packageInspections), snapshots
		);
	}

	private boolean startPlugin(LoadedPlugin plugin) {
		try {
			Path pluginData = this.dataDirectory.resolve(plugin.metadata().id());
			Files.createDirectories(pluginData);
			PluginResponse response = plugin.invoke(
					Protocol.Operation.INIT,
					new InitEvent(
							MinecraftVersionAdapter.gameVersion(),
							false,
							pluginData.toAbsolutePath().toString(),
							PluginEnvironment.CLIENT
					)
			);
			processResponse(plugin, response, Minecraft.getInstance(), 0);
			if (response.isError()) {
				plugin.disable();
				bridgeLog(plugin, "error", "Client initialization failed: " + response.error());
				return false;
			}
			plugin.markRunning();
			bridgeLog(plugin, "info", "Client plugin started");
			return true;
		} catch (Exception exception) {
			disable(plugin, "client initialization failed", exception);
			return false;
		}
	}

	private ClientTickEvent createTick(Minecraft client) {
		boolean connected = client.getConnection() != null;
		String address = connected
				? String.valueOf(client.getConnection().getConnection().getRemoteAddress())
				: null;
		String playerUuid = client.player == null ? null : client.player.getUUID().toString();
		String playerName = client.player == null ? null : client.player.getName().getString();
		String dimension = client.level == null ? null : MinecraftVersionAdapter.dimension(client.level);
		return new ClientTickEvent(
				this.tick, Instant.now().toEpochMilli(), connected, address,
				playerUuid, playerName, dimension
		);
	}

	private void invoke(LoadedPlugin plugin, Protocol.Operation operation, Object input, Minecraft client) {
		try {
			processResponse(plugin, plugin.invoke(operation, input), client, 0);
		} catch (RuntimeException exception) {
			disable(plugin, operation + " failed", exception);
		}
	}

	private void processResponse(
			LoadedPlugin plugin,
			PluginResponse response,
			Minecraft client,
			int systemCallDepth
	) {
		for (PluginLog log : response.logs()) {
			writeLog(plugin, log);
		}
		if (response.snapshot() != null) {
			bridgeLog(plugin, "warn", "Snapshot subscriptions are unavailable in the client runtime");
		}
		if (response.isPanic()) {
			disable(plugin, "client callback panicked: " + response.error(), null);
			return;
		}
		if (response.isError()) {
			bridgeLog(plugin, "error", "Client callback returned an error: " + response.error());
		}

		for (ActionRequest action : response.actions()) {
			executeAction(plugin, action, client);
		}

		if (response.systemCalls().isEmpty()) {
			return;
		}
		if (systemCallDepth >= MAX_SYSTEM_CALL_CHAIN) {
			disable(plugin, "client system call chain exceeded " + MAX_SYSTEM_CALL_CHAIN, null);
			return;
		}
		for (SystemCallRequest request : response.systemCalls()) {
			SystemCallResult unavailable = new SystemCallResult(
					request.id(), request.name(), false, JsonNull.INSTANCE,
					"System calls are unavailable in the client runtime"
			);
			try {
				processResponse(plugin, plugin.invoke(
						Protocol.Operation.SYSTEM_CALL_RESULT, unavailable
				), client, systemCallDepth + 1);
			} catch (RuntimeException exception) {
				disable(plugin, "client system call result callback failed", exception);
				return;
			}
		}
	}

	private void executeAction(LoadedPlugin plugin, ActionRequest action, Minecraft client) {
		if (!"minecraft:client.chat.display".equals(action.type())) {
			bridgeLog(plugin, "warn", "Rejected action in client runtime: " + action.type());
			return;
		}
		JsonElement message = action.payload() == null ? null : action.payload().get("message");
		if (message == null || !message.isJsonPrimitive() || !message.getAsJsonPrimitive().isString()) {
			bridgeLog(plugin, "warn", "Rejected malformed client chat display action");
			return;
		}
		if (client.player == null) {
			bridgeLog(plugin, "warn", "Cannot display client message outside a world");
			return;
		}
		client.player.sendSystemMessage(Component.literal(message.getAsString()));
	}

	private List<LoadedPlugin> runningPlugins() {
		return this.plugins.values().stream()
				.filter(plugin -> plugin.state() == PluginState.RUNNING)
				.toList();
	}

	private List<Path> nativeCandidates() {
		if (!Files.isDirectory(this.pluginDirectory)) {
			return List.of();
		}
		try (var files = Files.list(this.pluginDirectory)) {
			return files
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(nativeExtension()))
					.sorted(Comparator.comparing(Path::toString))
					.toList();
		} catch (IOException exception) {
			this.logger.error("Cannot scan client plugin directory {}", this.pluginDirectory, exception);
			return List.of();
		}
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
		bridgeLog(plugin, "error", "Plugin disabled: " + reason
				+ (throwable == null ? "" : " — " + rootMessage(throwable)));
		if (throwable == null) {
			this.logger.error("Disabled client Go plugin {}: {}", plugin.metadata().id(), reason);
		} else {
			this.logger.error("Disabled client Go plugin {}: {}", plugin.metadata().id(), reason, throwable);
		}
	}

	private void writeLog(LoadedPlugin plugin, PluginLog log) {
		plugin.appendLog(log);
		String message = "[Go/client/" + plugin.metadata().id() + "/" + log.stream() + "] " + log.message();
		switch (log.level() == null ? "info" : log.level()) {
			case "trace" -> this.logger.trace(message);
			case "debug" -> this.logger.debug(message);
			case "warn" -> this.logger.warn(message);
			case "error" -> this.logger.error(message);
			default -> this.logger.info(message);
		}
	}

	private void bridgeLog(LoadedPlugin plugin, String level, String message) {
		plugin.appendLog(new PluginLog("client-bridge", level, message, Instant.now().toEpochMilli()));
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
