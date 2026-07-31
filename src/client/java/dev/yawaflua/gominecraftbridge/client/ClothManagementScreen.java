package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
import dev.yawaflua.gominecraftbridge.client.runtime.ServerConnectionStatus;
import dev.yawaflua.gominecraftbridge.client.ui.CatalogScreenEdits;
import dev.yawaflua.gominecraftbridge.client.ui.CatalogScreenSection;
import dev.yawaflua.gominecraftbridge.client.ui.PackageScreenActions;
import dev.yawaflua.gominecraftbridge.client.ui.PackageScreenSection;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Composes the GBM management screen from independent UI sections. */
public final class ClothManagementScreen {
	private ClothManagementScreen() {
	}

	public static Screen create(Screen parent, boolean refresh) {
		GbmClientRuntime runtime = GoMinecraftBridgeClient.runtime();
		if (refresh) {
			runtime.requestRemoteRefresh();
		}

		BridgeManagementSnapshot local = runtime.localPlugins();
		BridgeManagementSnapshot remote = runtime.connectionStatus() == ServerConnectionStatus.AVAILABLE
				? runtime.remoteSnapshot()
				: null;
		CatalogScreenEdits catalogEdits = new CatalogScreenEdits(runtime.catalogSettings());
		PackageScreenActions localActions = new PackageScreenActions();
		PackageScreenActions remoteActions = new PackageScreenActions();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("GBM"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		CatalogScreenSection.add(builder, entries, runtime, catalogEdits);
		addLocalPackages(builder, entries, local, localActions);
		addRemotePackages(builder, entries, runtime, remote, remoteActions);
		builder.setSavingRunnable(() -> save(runtime, catalogEdits, localActions, remoteActions));

		Screen screen = builder.build();
		runtime.onUpdate(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.screen == screen) {
				client.setScreen(create(parent, false));
			}
		});
		return screen;
	}

	private static void addLocalPackages(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			BridgeManagementSnapshot snapshot,
			PackageScreenActions actions
	) {
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("Client packages"));
		PackageScreenSection.addOverview(
				category, entries, snapshot, actions, "Client",
				"config/gbm/client-plugins (legacy directory is also scanned)"
		);
		for (var plugin : snapshot.plugins()) {
			PackageScreenSection.addPlugin(
					builder, entries, plugin, snapshot.canReload(), actions,
					"Client", "Reload unavailable: the client native runtime is stopped."
			);
		}
	}

	private static void addRemotePackages(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			GbmClientRuntime runtime,
			BridgeManagementSnapshot snapshot,
			PackageScreenActions actions
	) {
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("Server packages"));
		if (snapshot == null) {
			PackageScreenSection.addConnectionStatus(category, entries, runtime.connectionStatus());
			return;
		}
		PackageScreenSection.addOverview(
				category, entries, snapshot, actions, "Server",
				"config/gbm/plugins or mods (legacy directory is also scanned)"
		);
		for (var plugin : snapshot.plugins()) {
			PackageScreenSection.addPlugin(
					builder, entries, plugin, snapshot.canReload(), actions,
					"Server", "Reload unavailable: server operator permission is required."
			);
		}
	}

	private static void save(
			GbmClientRuntime runtime,
			CatalogScreenEdits catalog,
			PackageScreenActions local,
			PackageScreenActions remote
	) {
		runtime.catalog().saveSettings(catalog.backendUrl(), catalog.automaticUpdates());
		if (catalog.runSearch()) {
			runtime.catalog().search(catalog.searchQuery());
		}
		catalog.installs().forEach(runtime.catalog()::install);

		if (local.rescan()) {
			runtime.rescanLocalPlugins();
		}
		local.reloads().forEach(runtime::reloadLocalPlugin);
		if (remote.rescan()) {
			runtime.requestRemoteRescan();
		}
		remote.reloads().forEach(runtime::requestRemoteReload);
		if (remote.reloads().isEmpty() && !remote.rescan() && runtime.remoteChannelAvailable()) {
			runtime.requestRemoteRefresh();
		}
	}
}
