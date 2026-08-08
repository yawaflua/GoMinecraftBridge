package dev.yawaflua.gominecraftbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ClientKeyMappingFactory {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("gbm", "native_plugins")
	);

	private ClientKeyMappingFactory() {
	}

	public static KeyMapping register(String id, InputConstants.Key defaultKey, String category) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				id,
				defaultKey.getType(),
				defaultKey.getValue(),
				CATEGORY
		));
	}

	public static String boundKeyName(KeyMapping mapping) {
		return KeyBindingHelper.getBoundKeyOf(mapping).getName();
	}
}
