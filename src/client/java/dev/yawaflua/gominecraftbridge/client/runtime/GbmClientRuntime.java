package dev.yawaflua.gominecraftbridge.client.runtime;

import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.catalog.CatalogSettings;
import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.client.ClientGoPluginManager;
import dev.yawaflua.gominecraftbridge.client.ClientHudState;
import dev.yawaflua.gominecraftbridge.client.catalog.CatalogTaskController;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.InteractionEvent;
import dev.yawaflua.gominecraftbridge.protocol.ClientChatEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Coordinates the local GBM client runtime without owning Fabric registration. */
public final class GbmClientRuntime implements AutoCloseable {
	private final ClientGoPluginManager plugins;
	private final Object modMenu;
	private final CatalogTaskController catalogTasks;
	private final Set<String> catalogUpdateCheckers = new HashSet<>();
	private Runnable updateListener;

	public GbmClientRuntime() {
		Logger clientLogger = LoggerFactory.getLogger("gbm/client");
		this.plugins = new ClientGoPluginManager(clientLogger);
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			try {
				this.modMenu = Class.forName("dev.yawaflua.gominecraftbridge.client.GbmModMenuAdapter")
						.getConstructor()
						.newInstance();
			} catch (Exception e) {
				throw new RuntimeException("Failed to initialize ModMenu adapter", e);
			}
		} else {
			this.modMenu = null;
		}
		this.catalogTasks = new CatalogTaskController(
				FabricLoader.getInstance().getConfigDir(),
				LoggerFactory.getLogger("gbm/catalog"),
				runnable -> Minecraft.getInstance().execute(runnable),
				this::synchronizeCatalog,
				this::rescanCatalogPackages,
				this::notifyUpdate
		);
	}

	public void start(Minecraft client) {
		this.plugins.discover();
		this.plugins.start(client);
		synchronizeCatalog();
		refreshModMenu();
		if (this.catalogTasks.settings().automaticUpdates()) {
			this.catalogTasks.installAutomaticUpdates();
		}
	}

	@Override
	public void close() {
		this.catalogTasks.close();
	}

	public void stop(Minecraft client) {
		this.plugins.stop(client);
		close();
	}

	public void tick(Minecraft client) {
		this.plugins.tick(client);
	}

	public void chat(ClientChatEvent event) {
		this.plugins.chat(event, Minecraft.getInstance());
	}

	public void interaction(InteractionEvent event) {
		this.plugins.interaction(event, Minecraft.getInstance());
	}

	public ClientHudState hud() {
		return this.plugins.hud();
	}

	public BridgeManagementSnapshot localPlugins() {
		return this.plugins.managementSnapshot("GBM client native runtime");
	}

	public ManagedPluginSnapshot localPlugin(String pluginId) {
		return localPlugins().plugins().stream()
				.filter(plugin -> plugin.metadata().id().equals(pluginId))
				.findFirst()
				.orElse(null);
	}

	public void rescanLocalPlugins() {
		this.plugins.rescan();
		synchronizeCatalog();
		refreshModMenu();
		notifyUpdate();
	}

	public void reloadLocalPlugin(String pluginId) {
		this.plugins.reload(pluginId, Minecraft.getInstance());
		notifyUpdate();
	}

	public ReloadResult updateLocalConfig(String pluginId, JsonObject config) {
		ReloadResult result = this.plugins.updateConfig(pluginId, config, Minecraft.getInstance());
		notifyUpdate();
		return result;
	}

	public CatalogTaskController catalog() {
		return this.catalogTasks;
	}

	public Object modMenu() {
		return this.modMenu;
	}

	public CatalogSettings catalogSettings() {
		return this.catalogTasks.settings();
	}

	public void onUpdate(Runnable listener) {
		this.updateListener = listener;
	}

	public void refreshModMenu() {
		if (this.modMenu != null) {
			try {
				Class.forName("dev.yawaflua.gominecraftbridge.client.runtime.ModMenuCompat")
						.getMethod("synchronize", Object.class, List.class)
						.invoke(null, this.modMenu, localPlugins().plugins());
			} catch (Exception e) {
				LoggerFactory.getLogger("gbm/client").warn("Failed to synchronize ModMenu", e);
			}
		}
	}

	private void rescanCatalogPackages() {
		this.plugins.rescan();
		synchronizeCatalog();
		refreshModMenu();
	}

	private void synchronizeCatalog() {
		GbmCatalogService catalog = this.catalogTasks.service();
		if (catalog == null) {
			return;
		}
		for (ManagedPluginSnapshot plugin : localPlugins().plugins()) {
			try {
				catalog.associatePlugin(Path.of(plugin.origin()), plugin.metadata().id());
			} catch (IOException | RuntimeException exception) {
				LoggerFactory.getLogger("gbm/catalog")
						.warn("Cannot associate GBM package {}", plugin.origin(), exception);
			}
		}
		synchronizeUpdateCheckers(catalog);
	}

	private void synchronizeUpdateCheckers(GbmCatalogService catalog) {
		if (this.modMenu != null) {
			try {
				Class.forName("dev.yawaflua.gominecraftbridge.client.runtime.ModMenuCompat")
						.getMethod("synchronizeUpdateCheckers", Object.class, GbmCatalogService.class, Set.class)
						.invoke(null, this.modMenu, catalog, this.catalogUpdateCheckers);
			} catch (Exception e) {
				LoggerFactory.getLogger("gbm/client").warn("Failed to synchronize ModMenu update checkers", e);
			}
		}
	}

	private void notifyUpdate() {
		Runnable listener = this.updateListener;
		if (listener != null) {
			listener.run();
		}
	}
}
