package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonElement;
import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.client.plugin.ClientPluginConfigStore;
import dev.yawaflua.gominecraftbridge.client.plugin.ClientPluginResponseHandler;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.host.NativePackageScanner;
import dev.yawaflua.gominecraftbridge.host.NativePluginRegistry;
import dev.yawaflua.gominecraftbridge.host.PluginState;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ClientTickEvent;
import dev.yawaflua.gominecraftbridge.protocol.BridgeCapabilities;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenEvent;
import dev.yawaflua.gominecraftbridge.protocol.ConfigUpdateEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.InitEvent;
import dev.yawaflua.gominecraftbridge.protocol.InteractionEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Client-process native host. It intentionally has its own package and data
 * directories and never delegates actions or system calls to a remote server.
 */
public final class ClientGoPluginManager {
	private final Logger logger;
	private final Path legacyPluginDirectory;
	private final Path managedPluginDirectory;
	private final NativePluginRegistry registry;
	private final ClientPluginConfigStore configStore;
	private final ClientHudState hud = new ClientHudState();
	private final ClientScreenController screens;
	private final ClientScreenCaptureController captures;
	private final ClientPluginResponseHandler responses;
	private long tick;
	private boolean running;

	public ClientGoPluginManager(Logger logger) {
		this.logger = logger;
		Path config = FabricLoader.getInstance().getConfigDir();
		Path root = config.resolve("gbm");
		this.legacyPluginDirectory = config.resolve("go-minecraft-bridge").resolve("client-plugins");
		this.managedPluginDirectory = root.resolve("client-plugins");
		this.configStore = new ClientPluginConfigStore(root.resolve("client-data"));
		this.screens = new ClientScreenController(this::handleScreenEvent);
		this.captures = new ClientScreenCaptureController(
				this::handleScreenCapture,
				this::handleCaptureWarning
		);
		this.responses = new ClientPluginResponseHandler(logger, this.hud, this.screens, this.captures);
		this.registry = new NativePluginRegistry(new NativePackageScanner(List.of(
				new NativePackageScanner.SearchRoot(this.legacyPluginDirectory, true),
				new NativePackageScanner.SearchRoot(this.managedPluginDirectory, true)
		)));
	}

	public synchronized void discover() {
		try {
			Files.createDirectories(this.legacyPluginDirectory);
			Files.createDirectories(this.managedPluginDirectory);
			this.configStore.initialize();
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot create client Go plugin directories", exception);
		}

		NativePluginRegistry.DiscoveryReport report = this.registry.discover(
				metadata -> metadata.environment().supportsClient()
		);
		for (NativePluginRegistry.SkippedPlugin skipped : report.skipped()) {
			this.logger.info("Skipping server-only GBM plugin {}", skipped.plugin().metadata().id());
		}
		for (NativePluginRegistry.PackageFailure failure : report.failures()) {
			this.logger.error("Cannot load client GBM plugin {}", failure.path(), failure.cause());
		}
		for (NativePackageScanner.ScanFailure failure : report.scanFailures()) {
			this.logger.error("Cannot scan client plugin directory {}", failure.root(), failure.cause());
		}
		for (LoadedPlugin plugin : report.discovered()) {
			this.responses.bridgeLog(plugin, "info", "Client package check passed: " + plugin.backend().origin());
			this.logger.info(
					"Discovered client GBM plugin {} {} from {}",
					plugin.metadata().name(), plugin.metadata().version(), plugin.backend().origin().getFileName()
			);
			if (this.running) {
				startPlugin(plugin);
			}
		}
	}

	public synchronized void start(Minecraft client) {
		if (this.running) {
			return;
		}
		this.running = true;
		for (LoadedPlugin plugin : this.registry.plugins()) {
			startPlugin(plugin);
		}
	}

	public synchronized void tick(Minecraft client) {
		this.tick++;
		this.hud.pruneExpired();
		this.screens.tick(client);
		for (LoadedPlugin plugin : runningPlugins()) {
			this.responses.invoke(plugin, Protocol.Operation.CLIENT_TICK, createTick(client), client);
		}
		this.captures.tick(client);
	}

	public synchronized void interaction(InteractionEvent event, Minecraft client) {
		for (LoadedPlugin plugin : runningPlugins()) {
			this.responses.invoke(
					plugin, Protocol.Operation.INTERACTION, ClientProtocolInput.scoped(event), client
			);
		}
	}

	public synchronized void stop(Minecraft client) {
		for (LoadedPlugin plugin : runningPlugins()) {
			try {
					PluginResponse response = plugin.invoke(
							Protocol.Operation.DEINIT,
							ClientProtocolInput.scoped(new DeinitEvent("client_stopping"))
				);
				this.responses.process(plugin, response, client);
			} catch (RuntimeException exception) {
				this.responses.bridgeLog(plugin, "error", "Client deinit failed: " + ClientPluginResponseHandler.rootMessage(exception));
				this.logger.error("Client Go plugin {} failed during deinit", plugin.metadata().id(), exception);
			} finally {
				this.hud.clear(plugin.metadata().id());
				this.captures.clear(plugin);
				this.screens.clear(plugin, client);
				plugin.markStopped();
			}
		}
		this.running = false;
		this.captures.close();
	}

	public synchronized ReloadResult rescan() {
		int before = this.registry.plugins().size();
		discover();
		return new ReloadResult(true, "Client package check completed; new plugins: "
				+ (this.registry.plugins().size() - before));
	}

	public synchronized ReloadResult reload(String pluginId, Minecraft client) {
		LoadedPlugin plugin = this.registry.plugin(pluginId);
		if (plugin == null) {
			return new ReloadResult(false, "Unknown client plugin: " + pluginId);
		}
		if (plugin.state() == PluginState.RUNNING) {
			try {
					this.responses.process(plugin, plugin.invoke(
							Protocol.Operation.DEINIT, ClientProtocolInput.scoped(new DeinitEvent("client_admin_reload"))
				), client);
			} catch (RuntimeException exception) {
				this.responses.bridgeLog(plugin, "error", "Client reload deinit failed: " + ClientPluginResponseHandler.rootMessage(exception));
			}
		}
		this.hud.clear(pluginId);
		this.captures.clear(plugin);
		this.screens.clear(plugin, client);
		plugin.prepareReload();
		boolean started = startPlugin(plugin);
		return new ReloadResult(started, started
				? "Client plugin " + pluginId + " restarted (the native binary remains loaded)"
				: "Client plugin " + pluginId + " failed to restart");
	}

	public synchronized ReloadResult updateConfig(String pluginId, JsonElement submitted, Minecraft client) {
		LoadedPlugin plugin = this.registry.plugin(pluginId);
		if (plugin == null) {
			return new ReloadResult(false, "Unknown client plugin: " + pluginId);
		}
		if (plugin.metadata().configSchema() == null || !plugin.metadata().configWritable()) {
			return new ReloadResult(false, "Plugin " + pluginId + " does not expose a configuration");
		}
		if (submitted == null || !submitted.isJsonObject()) {
			return new ReloadResult(false, "Plugin configuration must be a JSON object");
		}

		try {
			PluginResponse response = plugin.invoke(
					Protocol.Operation.CONFIG_UPDATE,
					ClientProtocolInput.scoped(new ConfigUpdateEvent(submitted.getAsJsonObject()))
			);
			this.responses.process(plugin, response, client);
			if (response.isError()) {
				return new ReloadResult(false, "Plugin rejected the configuration: " + response.error());
			}

			JsonElement accepted = response.data();
			var snapshot = accepted != null && accepted.isJsonObject()
					? accepted.getAsJsonObject()
					: submitted.getAsJsonObject();
			plugin.configSnapshot(snapshot);
			this.configStore.write(pluginId, snapshot);
			this.responses.bridgeLog(plugin, "info", "Configuration updated from Cloth Config");
			return new ReloadResult(true, "Plugin " + pluginId + " configuration updated");
		} catch (RuntimeException | IOException exception) {
			this.responses.bridgeLog(plugin, "error", "Configuration update failed: " + ClientPluginResponseHandler.rootMessage(exception));
			return new ReloadResult(false, "Cannot update plugin configuration: " + ClientPluginResponseHandler.rootMessage(exception));
		}
	}

	public synchronized BridgeManagementSnapshot managementSnapshot(String message) {
		List<ManagedPluginSnapshot> snapshots = this.registry.plugins().stream()
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
				this.registry.inspections(), snapshots
		);
	}

	public ClientHudState hud() {
		return this.hud;
	}

	private boolean startPlugin(LoadedPlugin plugin) {
		try {
			Path pluginData = this.configStore.dataDirectory(plugin.metadata().id());
			loadSavedConfig(plugin, Minecraft.getInstance());
			PluginResponse response = plugin.invoke(
					Protocol.Operation.INIT,
					new InitEvent(
							MinecraftVersionAdapter.gameVersion(),
							false,
							pluginData.toAbsolutePath().toString(),
							PluginEnvironment.CLIENT,
							BridgeCapabilities.client()
					)
			);
			this.responses.process(plugin, response, Minecraft.getInstance());
			if (response.isError()) {
				plugin.disable();
				this.responses.bridgeLog(plugin, "error", "Client initialization failed: " + response.error());
				return false;
			}
			plugin.markRunning();
			this.responses.bridgeLog(plugin, "info", "Client plugin started");
			return true;
		} catch (Exception exception) {
			this.responses.disable(plugin, "client initialization failed", exception);
			return false;
		}
	}

	private void loadSavedConfig(LoadedPlugin plugin, Minecraft client) {
		if (plugin.metadata().configSchema() == null || !plugin.metadata().configWritable()) {
			return;
		}
		try {
			var saved = this.configStore.read(plugin.metadata().id());
			if (saved == null) {
				return;
			}
			PluginResponse response = plugin.invoke(
					Protocol.Operation.CONFIG_UPDATE,
					ClientProtocolInput.scoped(new ConfigUpdateEvent(saved))
			);
			this.responses.process(plugin, response, client);
			if (response.isError()) {
				throw new IllegalArgumentException(response.error());
			}
			JsonElement accepted = response.data();
			plugin.configSnapshot(accepted != null && accepted.isJsonObject()
					? accepted.getAsJsonObject()
					: saved);
			this.responses.bridgeLog(plugin, "info", "Loaded saved configuration");
		} catch (Exception exception) {
			this.responses.bridgeLog(plugin, "error", "Cannot load saved configuration: " + ClientPluginResponseHandler.rootMessage(exception));
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

	private synchronized void handleScreenEvent(LoadedPlugin plugin, ClientScreenEvent event) {
		if (plugin.state() == PluginState.RUNNING) {
			this.responses.invoke(plugin, Protocol.Operation.CLIENT_SCREEN_EVENT, event, Minecraft.getInstance());
		}
	}

	private synchronized void handleScreenCapture(LoadedPlugin plugin, byte[] frame) {
		if (plugin.state() != PluginState.RUNNING) {
			return;
		}
		try {
			this.responses.process(
					plugin,
					plugin.invokeRaw(Protocol.Operation.CLIENT_SCREEN_CAPTURE, frame),
					Minecraft.getInstance()
			);
		} catch (RuntimeException exception) {
			this.responses.disable(plugin, "client screen capture callback failed", exception);
		}
	}

	private synchronized void handleCaptureWarning(LoadedPlugin plugin, String message) {
		this.responses.bridgeLog(plugin, "warn", message);
	}

	private List<LoadedPlugin> runningPlugins() {
		return this.registry.runningPlugins();
	}
}
