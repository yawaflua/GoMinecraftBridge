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
}
