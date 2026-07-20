package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.HudElementDto;
import dev.yawaflua.gominecraftbridge.protocol.HudScene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe immutable snapshots consumed by the HUD render callback. */
public final class ClientHudState {
	private static final int MAX_ELEMENTS_PER_PLUGIN = 1_024;
	private static final int MAX_TEXT_LENGTH = 2_048;
	private static final int MAX_COORDINATE = 100_000;
	private static final long MAX_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1_000;

	private final Map<String, LinkedHashMap<String, StoredElement>> scenes = new LinkedHashMap<>();
	private volatile List<HudElementDto> rendered = List.of();

	public synchronized void replace(String pluginId, HudScene scene) {
		if (scene.elements().size() > MAX_ELEMENTS_PER_PLUGIN) {
			throw new IllegalArgumentException("HUD scene exceeds " + MAX_ELEMENTS_PER_PLUGIN + " elements");
		}
		LinkedHashMap<String, StoredElement> replacement = new LinkedHashMap<>();
		long now = System.currentTimeMillis();
		for (int index = 0; index < scene.elements().size(); index++) {
			HudElementDto element = scene.elements().get(index);
			validate(element);
			String key = hasId(element.id()) ? element.id() : "$scene:" + index;
			if (replacement.put(key, store(element, now)) != null) {
				throw new IllegalArgumentException("Duplicate HUD element id: " + element.id());
			}
		}
		if (scene.elements().isEmpty()) {
			this.scenes.remove(pluginId);
		} else {
			this.scenes.put(pluginId, replacement);
		}
		publish();
	}

	public synchronized void upsert(String pluginId, HudElementDto element) {
		validate(element);
		if (!hasId(element.id())) {
			throw new IllegalArgumentException("A HUD element rendered individually requires an id");
		}
		LinkedHashMap<String, StoredElement> elements = this.scenes.computeIfAbsent(
				pluginId, ignored -> new LinkedHashMap<>()
		);
		if (!elements.containsKey(element.id()) && elements.size() >= MAX_ELEMENTS_PER_PLUGIN) {
			throw new IllegalArgumentException("HUD scene exceeds " + MAX_ELEMENTS_PER_PLUGIN + " elements");
		}
		elements.put(element.id(), store(element, System.currentTimeMillis()));
		publish();
	}

	public synchronized void remove(String pluginId, String elementId) {
		LinkedHashMap<String, StoredElement> elements = this.scenes.get(pluginId);
		if (elements == null || elements.remove(elementId) == null) {
			return;
		}
		if (elements.isEmpty()) {
			this.scenes.remove(pluginId);
		}
		publish();
	}

	public synchronized void pruneExpired() {
		long now = System.currentTimeMillis();
		boolean changed = false;
		var iterator = this.scenes.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			changed |= entry.getValue().values().removeIf(element -> element.expired(now));
			if (entry.getValue().isEmpty()) {
				iterator.remove();
			}
		}
		if (changed) {
			publish();
		}
	}

	public synchronized void clear(String pluginId) {
		if (this.scenes.remove(pluginId) != null) {
			publish();
		}
	}

	public List<HudElementDto> elements() {
		return this.rendered;
	}

	private void publish() {
		this.rendered = this.scenes.values().stream()
				.flatMap(elements -> elements.values().stream())
				.map(StoredElement::element)
				.toList();
	}

	private static void validate(HudElementDto element) {
		if (element == null) {
			throw new IllegalArgumentException("HUD element is null");
		}
		if (Math.abs((long) element.x()) > MAX_COORDINATE || Math.abs((long) element.y()) > MAX_COORDINATE) {
			throw new IllegalArgumentException("HUD coordinates are out of range");
		}
		if (!validAnchor(element.anchor())) {
			throw new IllegalArgumentException("Unknown HUD anchor: " + element.anchor());
		}
		if (element.color() < 0 || element.color() > 0xffff_ffffL) {
			throw new IllegalArgumentException("HUD color must be an unsigned 32-bit ARGB value");
		}
		if (element.durationMillis() < 0 || element.durationMillis() > MAX_DURATION_MILLIS) {
			throw new IllegalArgumentException("HUD duration is out of range");
		}
		switch (element.type()) {
			case "text" -> {
				if (element.text() == null || element.text().length() > MAX_TEXT_LENGTH) {
					throw new IllegalArgumentException("HUD text is missing or too long");
				}
			}
			case "rectangle" -> {
				if (element.width() < 0 || element.height() < 0
						|| element.width() > MAX_COORDINATE || element.height() > MAX_COORDINATE) {
					throw new IllegalArgumentException("HUD rectangle size is out of range");
				}
			}
			default -> throw new IllegalArgumentException("Unknown HUD element type: " + element.type());
		}
	}

	private static StoredElement store(HudElementDto element, long now) {
		long expiresAt = element.durationMillis() == 0 ? 0 : now + element.durationMillis();
		return new StoredElement(element, expiresAt);
	}

	private static boolean hasId(String id) {
		return id != null && !id.isBlank() && id.length() <= 128;
	}

	private static boolean validAnchor(String anchor) {
		return anchor == null || switch (anchor) {
			case "top_left", "top_center", "top_right",
					"center_left", "center", "center_right",
					"bottom_left", "bottom_center", "bottom_right" -> true;
			default -> false;
		};
	}

	private record StoredElement(HudElementDto element, long expiresAtUnixMilli) {
		boolean expired(long now) {
			return this.expiresAtUnixMilli != 0 && now >= this.expiresAtUnixMilli;
		}
	}
}
