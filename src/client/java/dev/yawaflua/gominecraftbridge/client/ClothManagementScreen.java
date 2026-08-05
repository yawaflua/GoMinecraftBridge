package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
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

	public static Screen create(Screen parent) {
		GbmClientRuntime runtime = GoMinecraftBridgeClient.runtime();

		BridgeManagementSnapshot local = runtime.localPlugins();
		CatalogScreenEdits catalogEdits = new CatalogScreenEdits(runtime.catalogSettings());
		PackageScreenActions localActions = new PackageScreenActions();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("GBM"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		CatalogScreenSection.add(builder, entries, runtime, catalogEdits);
		addLocalPackages(builder, entries, local, localActions);
		builder.setSavingRunnable(() -> save(runtime, catalogEdits, localActions));

		Screen screen = builder.build();
		runtime.onUpdate(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.screen == screen) {
				client.setScreen(create(parent));
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

	private static void save(
			GbmClientRuntime runtime,
			CatalogScreenEdits catalog,
			PackageScreenActions local
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
	}
}
