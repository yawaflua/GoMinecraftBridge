package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.UpdateChecker;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Map;

public final class GoBridgeModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
				return parent;
			}
			return ClothManagementScreen.create(parent, true);
		};
	}

	@Override
	public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return Map.of();
		}
		var runtime = GoMinecraftBridgeClient.runtime();
		return runtime.modMenu().configScreenFactories(runtime.localPlugins().plugins());
	}

	@Override
	public Map<String, UpdateChecker> getProvidedUpdateCheckers() {
		return GoMinecraftBridgeClient.runtime().modMenu().updateCheckers();
	}
}
