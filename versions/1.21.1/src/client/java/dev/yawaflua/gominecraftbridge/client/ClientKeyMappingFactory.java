package dev.yawaflua.gominecraftbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class ClientKeyMappingFactory {
	private ClientKeyMappingFactory() {
	}

	public static KeyMapping register(String id, InputConstants.Key defaultKey, String category) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				id,
				defaultKey.getType(),
				defaultKey.getValue(),
				category
		));
	}

	public static String boundKeyName(KeyMapping mapping) {
		return KeyBindingHelper.getBoundKeyOf(mapping).getName();
	}
}
