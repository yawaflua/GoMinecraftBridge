package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

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
}
