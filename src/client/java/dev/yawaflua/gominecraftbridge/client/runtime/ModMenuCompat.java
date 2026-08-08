package dev.yawaflua.gominecraftbridge.client.runtime;

import com.terraformersmc.modmenu.api.UpdateChannel;
import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.catalog.CatalogUpdate;
import dev.yawaflua.gominecraftbridge.catalog.InstalledCatalogPackage;
import dev.yawaflua.gominecraftbridge.client.GbmModMenuAdapter;
import dev.yawaflua.gominecraftbridge.client.NativePluginUpdateInfo;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class ModMenuCompat {
	public static void synchronize(Object modMenu, List<ManagedPluginSnapshot> plugins) {
		((GbmModMenuAdapter) modMenu).synchronize(plugins);
	}

	public static void synchronizeUpdateCheckers(Object modMenu, GbmCatalogService catalog, Set<String> catalogUpdateCheckers) {
		synchronized (catalogUpdateCheckers) {
			GbmModMenuAdapter adapter = (GbmModMenuAdapter) modMenu;
			for (String pluginId : Set.copyOf(catalogUpdateCheckers)) {
				adapter.unregisterUpdateChecker(pluginId);
			}
			catalogUpdateCheckers.clear();
			if (!catalog.settings().automaticUpdates()) {
				return;
			}
			for (InstalledCatalogPackage installed : catalog.installedPackages()) {
				if (installed.pluginId().isBlank()) {
					continue;
				}
				adapter.registerUpdateChecker(installed.pluginId(), () -> {
					try {
						CatalogUpdate update = catalog.checkForUpdate(installed.projectId());
						if (update == null || !update.updateAvailable()) {
							return null;
						}
						return new NativePluginUpdateInfo(
								catalog.downloadUrl(installed, update.latestVersion()),
								UpdateChannel.RELEASE
						);
					} catch (IOException | RuntimeException exception) {
						return null;
					}
				});
				catalogUpdateCheckers.add(installed.pluginId());
			}
		}
	}
}
