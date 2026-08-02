package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenEvent;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.function.BiConsumer;

public final class ClientScreenController {
	private final BiConsumer<LoadedPlugin, ClientScreenEvent> events;
	private LoadedPlugin owner;
	private ClientFormScreen active;
	private Screen parent;

	public ClientScreenController(BiConsumer<LoadedPlugin, ClientScreenEvent> events) {
		this.events = events;
	}

	public void open(LoadedPlugin plugin, ClientScreenSpec spec, Minecraft client) {
		ClientScreenSpecValidator.validate(spec);
		if (this.owner != null && this.owner != plugin) {
			throw new IllegalStateException("Another Go plugin owns the active client screen");
		}
		if (this.owner == null) {
			this.parent = client.screen;
		}
		this.owner = plugin;
		ClientFormScreen next = new ClientFormScreen(this.parent, spec, new Callbacks(plugin, spec.id(), client));
		this.active = next;
		client.setScreen(next);
	}

	public void close(LoadedPlugin plugin, String screenId, Minecraft client, boolean notify) {
		if (this.owner != plugin || this.active == null || !this.active.screenId().equals(screenId)) {
			return;
		}
		Map<String, String> values = this.active.values();
		Screen restore = this.parent;
		clear();
		client.setScreen(restore);
		if (notify) {
			this.events.accept(plugin, new ClientScreenEvent(screenId, "closed", null, "programmatic", values));
		}
	}

	public void tick(Minecraft client) {
		if (this.owner == null || this.active == null || client.screen == this.active) {
			return;
		}
		LoadedPlugin plugin = this.owner;
		String screenId = this.active.screenId();
		Map<String, String> values = this.active.values();
		clear();
		this.events.accept(plugin, new ClientScreenEvent(screenId, "closed", null, "replaced", values));
	}

	public void clear(LoadedPlugin plugin, Minecraft client) {
		if (this.owner != plugin) {
			return;
		}
		Screen restore = this.parent;
		clear();
		if (client.screen instanceof ClientFormScreen) {
			client.setScreen(restore);
		}
	}

	private void clear() {
		this.owner = null;
		this.active = null;
		this.parent = null;
	}

	private final class Callbacks implements ClientScreenCallbacks {
		private final LoadedPlugin plugin;
		private final String screenId;
		private final Minecraft client;

		private Callbacks(LoadedPlugin plugin, String screenId, Minecraft client) {
			this.plugin = plugin;
			this.screenId = screenId;
			this.client = client;
		}

		@Override
		public void button(ClientScreenButton button, Map<String, String> values) {
			if (button.close()) {
				Screen restore = ClientScreenController.this.parent;
				clear();
				this.client.setScreen(restore);
			}
			ClientScreenController.this.events.accept(this.plugin, new ClientScreenEvent(
					this.screenId, "button", button.id(), null, values
			));
		}

		@Override
		public void closed(Map<String, String> values) {
			Screen restore = ClientScreenController.this.parent;
			clear();
			this.client.setScreen(restore);
			ClientScreenController.this.events.accept(this.plugin, new ClientScreenEvent(
					this.screenId, "closed", null, "escape", values
			));
		}
	}
}
