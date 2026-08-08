package dev.yawaflua.gominecraftbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.host.PluginState;
import dev.yawaflua.gominecraftbridge.protocol.ClientKeyBinding;
import dev.yawaflua.gominecraftbridge.protocol.ClientKeyEvent;
import net.minecraft.client.KeyMapping;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ClientKeyBindingController {
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]{1,64}");
	private static final int MAX_BINDINGS_PER_PLUGIN = 64;
	private final Sink sink;
	private final Map<String, RegisteredBinding> registered = new HashMap<>();
	private boolean registrationOpen = true;

	public ClientKeyBindingController(Sink sink) {
		this.sink = sink;
	}

	public synchronized void register(LoadedPlugin plugin) {
		List<ClientKeyBinding> declarations = plugin.metadata().clientKeyBindings();
		if (declarations.size() > MAX_BINDINGS_PER_PLUGIN) {
			throw new IllegalArgumentException("Plugin " + plugin.metadata().id() + " declares too many client key bindings");
		}
		Map<String, ClientKeyBinding> unique = new LinkedHashMap<>();
		for (ClientKeyBinding declaration : declarations) {
			validate(plugin, declaration);
			if (unique.putIfAbsent(declaration.id(), declaration) != null) {
				throw new IllegalArgumentException("Duplicate client key binding id " + declaration.id()
						+ " in plugin " + plugin.metadata().id());
			}
		}
		for (ClientKeyBinding declaration : unique.values()) {
			String bindingId = plugin.metadata().id() + "." + declaration.id();
			RegisteredBinding existing = this.registered.get(bindingId);
			if (existing != null) {
				existing.plugin = plugin;
				continue;
			}
			if (!this.registrationOpen) {
				throw new IllegalStateException("Client key bindings for plugin " + plugin.metadata().id()
						+ " cannot be registered after client startup");
			}
			InputConstants.Key defaultKey;
			try {
				defaultKey = InputConstants.getKey(declaration.defaultKey());
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid default key " + declaration.defaultKey()
						+ " for client key binding " + bindingId, exception);
			}
			KeyMapping mapping = ClientKeyMappingFactory.register(
					"GBM / " + plugin.metadata().id() + " / " + declaration.name(),
					defaultKey,
					"GBM"
			);
			this.registered.put(bindingId, new RegisteredBinding(plugin, declaration.id(), mapping));
		}
	}

	public synchronized void tick() {
		this.registrationOpen = false;
		long timestamp = Instant.now().toEpochMilli();
		for (RegisteredBinding binding : this.registered.values()) {
			while (binding.mapping.consumeClick()) {
				if (binding.plugin.state() != PluginState.RUNNING) {
					continue;
				}
				this.sink.accept(binding.plugin, new ClientKeyEvent(
						binding.id,
						ClientKeyMappingFactory.boundKeyName(binding.mapping),
						timestamp
				));
				if (binding.plugin.state() != PluginState.RUNNING) {
					break;
				}
			}
		}
	}

	private static void validate(LoadedPlugin plugin, ClientKeyBinding declaration) {
		if (declaration == null) {
			throw new IllegalArgumentException("Plugin " + plugin.metadata().id() + " declares a null client key binding");
		}
		if (declaration.id() == null || !ID_PATTERN.matcher(declaration.id()).matches()) {
			throw new IllegalArgumentException("Invalid client key binding id in plugin " + plugin.metadata().id());
		}
		if (declaration.name() == null || declaration.name().isBlank() || declaration.name().length() > 128) {
			throw new IllegalArgumentException("Invalid client key binding name " + declaration.id()
					+ " in plugin " + plugin.metadata().id());
		}
		if (declaration.defaultKey() == null || declaration.defaultKey().isBlank()) {
			throw new IllegalArgumentException("Default key is required for client key binding " + declaration.id()
					+ " in plugin " + plugin.metadata().id());
		}
	}

	private static final class RegisteredBinding {
		private LoadedPlugin plugin;
		private final String id;
		private final KeyMapping mapping;

		private RegisteredBinding(LoadedPlugin plugin, String id, KeyMapping mapping) {
			this.plugin = plugin;
			this.id = id;
			this.mapping = mapping;
		}
	}

	@FunctionalInterface
	public interface Sink {
		void accept(LoadedPlugin plugin, ClientKeyEvent event);
	}
}
