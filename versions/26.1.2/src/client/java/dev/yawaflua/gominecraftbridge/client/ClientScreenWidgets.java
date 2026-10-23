package dev.yawaflua.gominecraftbridge.client;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

final class ClientScreenWidgets {
	private ClientScreenWidgets() {
	}

	static void add(Screen screen, AbstractWidget widget) {
		Screens.getWidgets(screen).add(widget);
	}

	static int bottomOfCenteredButtons(Screen screen, int fallback) {
		int bottom = -1;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			int center = widget.getX() + widget.getWidth() / 2;
			if (widget.visible && widget.getHeight() == 20 && Math.abs(center - screen.width / 2) <= 110) {
				bottom = Math.max(bottom, widget.getY() + widget.getHeight());
			}
		}
		return bottom < 0 ? fallback : bottom;
	}
}
