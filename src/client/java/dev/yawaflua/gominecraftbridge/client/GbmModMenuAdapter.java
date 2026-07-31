package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.UpdateChecker;
import com.terraformersmc.modmenu.util.mod.Mod;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The only component allowed to mutate Mod Menu's native-plugin model. */
public final class GbmModMenuAdapter {
	private final Set<String> registeredIds = new HashSet<>();
	private final Map<String, UpdateChecker> updateCheckers = new LinkedHashMap<>();

	public synchronized void registerUpdateChecker(String pluginId, UpdateChecker checker) {
		if (pluginId == null || pluginId.isBlank()) {
			throw new IllegalArgumentException("Plugin id is required");
		}
		if (checker == null) {
			throw new IllegalArgumentException("Update checker is required");
		}
		this.updateCheckers.put(pluginId, checker);
		Mod existing = ModMenu.MODS.get(pluginId);
		if (existing instanceof NativeModMenuEntry entry) {
			entry.setUpdateChecker(checker);
			entry.setUpdateInfo(null);
			ModMenu.checkForUpdates();
		}
	}

	public synchronized void unregisterUpdateChecker(String pluginId) {
		this.updateCheckers.remove(pluginId);
		Mod existing = ModMenu.MODS.get(pluginId);
		if (existing instanceof NativeModMenuEntry entry) {
			entry.setUpdateChecker(null);
			entry.setUpdateInfo(null);
		}
	}

	public synchronized void synchronize(List<ManagedPluginSnapshot> plugins) {
		Set<String> currentIds = new HashSet<>();
		boolean checkNewEntries = false;
		for (ManagedPluginSnapshot plugin : plugins) {
			PluginMetadata metadata = plugin.metadata();
			if (metadata == null) {
				continue;
			}
			String id = metadata.id();
			Mod existing = ModMenu.MODS.get(id);
			if (existing != null && !(existing instanceof NativeModMenuEntry)) {
				continue;
			}

			currentIds.add(id);
			NativeModMenuEntry entry = existing instanceof NativeModMenuEntry nativeEntry
					&& nativeEntry.metadata().equals(metadata)
					? nativeEntry
					: new NativeModMenuEntry(metadata);
			UpdateChecker checker = this.updateCheckers.get(id);
			checkNewEntries |= checker != null && entry.getUpdateChecker() != checker;
			entry.setUpdateChecker(checker);
			ModMenu.MODS.put(id, entry);
			ModMenu.ROOT_MODS.put(id, entry);
		}

		for (String removedId : Set.copyOf(this.registeredIds)) {
			if (!currentIds.contains(removedId)) {
				removeNativeEntry(removedId);
			}
		}
		this.registeredIds.clear();
		this.registeredIds.addAll(currentIds);
		ModMenu.clearModCountCache();
		if (checkNewEntries) {
			ModMenu.checkForUpdates();
		}
	}

	public synchronized Map<String, UpdateChecker> updateCheckers() {
		Map<String, UpdateChecker> active = new LinkedHashMap<>();
		for (String id : this.registeredIds) {
			UpdateChecker checker = this.updateCheckers.get(id);
			if (checker != null) {
				active.put(id, checker);
			}
		}
		return Map.copyOf(active);
	}

	public synchronized Map<String, ConfigScreenFactory<?>> configScreenFactories(
			List<ManagedPluginSnapshot> plugins
	) {
		Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
		for (ManagedPluginSnapshot plugin : plugins) {
			PluginMetadata metadata = plugin.metadata();
			if (this.registeredIds.contains(metadata.id())
					&& metadata.configSchema() != null
					&& metadata.configWritable()) {
				factories.put(metadata.id(), parent -> NativePluginConfigScreen.create(parent, metadata.id()));
			}
		}
		return Map.copyOf(factories);
	}

	private static void removeNativeEntry(String pluginId) {
		ModMenu.MODS.computeIfPresent(pluginId, (id, entry) ->
				entry instanceof NativeModMenuEntry ? null : entry
		);
		ModMenu.ROOT_MODS.computeIfPresent(pluginId, (id, entry) ->
				entry instanceof NativeModMenuEntry ? null : entry
		);
	}
}
