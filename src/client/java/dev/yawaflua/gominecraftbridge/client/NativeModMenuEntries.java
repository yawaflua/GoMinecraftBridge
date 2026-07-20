package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.util.mod.Mod;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Keeps Mod Menu's model synchronized with locally loaded native plugins. */
public final class NativeModMenuEntries {
	private static final Set<String> REGISTERED_IDS = new HashSet<>();

	private NativeModMenuEntries() {
	}

	public static synchronized void synchronize() {
		Set<String> currentIds = new HashSet<>();
		for (var plugin : GoMinecraftBridgeClient.localPlugins().plugins()) {
			PluginMetadata metadata = plugin.metadata();
			if (metadata == null) {
				continue;
			}

			String id = metadata.id();
			Mod existing = ModMenu.MODS.get(id);
			if (existing != null && !(existing instanceof NativeModMenuEntry)) {
				// Never replace a real Fabric mod if a native plugin reused its id.
				continue;
			}

			currentIds.add(id);
			NativeModMenuEntry entry = new NativeModMenuEntry(metadata);
			ModMenu.MODS.put(id, entry);
			ModMenu.ROOT_MODS.put(id, entry);
		}

		for (String removedId : Set.copyOf(REGISTERED_IDS)) {
			if (currentIds.contains(removedId)) {
				continue;
			}
			ModMenu.MODS.computeIfPresent(removedId, (id, entry) ->
					entry instanceof NativeModMenuEntry ? null : entry
			);
			ModMenu.ROOT_MODS.computeIfPresent(removedId, (id, entry) ->
					entry instanceof NativeModMenuEntry ? null : entry
			);
		}

		REGISTERED_IDS.clear();
		REGISTERED_IDS.addAll(currentIds);
		ModMenu.clearModCountCache();
	}

	/** Config factories queried by Mod Menu for the emulated native entries. */
	public static synchronized Map<String, ConfigScreenFactory<?>> configScreenFactories() {
		Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
		for (String id : REGISTERED_IDS) {
			var plugin = GoMinecraftBridgeClient.localPlugin(id);
			if (plugin != null && plugin.metadata().configSchema() != null
					&& plugin.metadata().configWritable()) {
				factories.put(id, parent -> NativePluginConfigScreen.create(parent, id));
			}
		}
		return Map.copyOf(factories);
	}
}
