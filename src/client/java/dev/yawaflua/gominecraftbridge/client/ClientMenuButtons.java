package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientMenuButtons {
	public static final String TITLE = "title";
	public static final String PAUSE = "pause";

	public record Entry(LoadedPlugin plugin, String id, String label, String screen, int x, int y, int width) {
		public boolean positioned() {
			return this.width > 0;
		}
	}

	private final Map<String, Entry> buttons = new LinkedHashMap<>();

	public synchronized void add(LoadedPlugin plugin, String id, String label, String screen, int x, int y, int width) {
		if (id == null || id.isBlank() || label == null || label.isBlank()) {
			throw new IllegalArgumentException("Menu button requires id and label");
		}
		String target = screen == null || screen.isBlank() ? TITLE : screen;
		if (!TITLE.equals(target) && !PAUSE.equals(target)) {
			throw new IllegalArgumentException("Unknown menu screen for button: " + screen);
		}
		if (width < 0 || width > 1_024 || Math.abs(x) > 4_096 || Math.abs(y) > 4_096) {
			throw new IllegalArgumentException("Menu button bounds are out of range");
		}
		this.buttons.put(plugin.metadata().id() + "\0" + id, new Entry(plugin, id, label, target, x, y, width));
	}

	public synchronized void remove(LoadedPlugin plugin, String id) {
		this.buttons.remove(plugin.metadata().id() + "\0" + id);
	}

	public synchronized void clear(LoadedPlugin plugin) {
		this.buttons.values().removeIf(entry -> entry.plugin() == plugin);
	}

	public synchronized List<Entry> snapshot(String screen) {
		List<Entry> entries = new ArrayList<>();
		for (Entry entry : this.buttons.values()) {
			if (entry.screen().equals(screen)) {
				entries.add(entry);
			}
		}
		return List.copyOf(entries);
	}
}
