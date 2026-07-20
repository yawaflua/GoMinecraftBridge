package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

/** Replaces one client plugin's complete retained HUD scene. */
public record HudScene(List<HudElementDto> elements) {
	public HudScene {
		elements = elements == null ? List.of() : List.copyOf(elements);
	}
}
