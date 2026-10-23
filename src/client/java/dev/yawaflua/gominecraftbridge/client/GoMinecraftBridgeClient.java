package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
import dev.yawaflua.gominecraftbridge.fabric.GbmFabricInteractionAdapter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class GoMinecraftBridgeClient implements ClientModInitializer {
	private static final GbmClientRuntime RUNTIME = new GbmClientRuntime();

	public static GbmClientRuntime runtime() {
		return RUNTIME;
	}

	@Override
	public void onInitializeClient() {
		try {
			Minecraft client = Minecraft.getInstance();
			RUNTIME.start(client);
			ClientHudRendering.register(RUNTIME.hud());
			GbmFabricInteractionAdapter.register(true, (event, player) -> RUNTIME.interaction(event));
			ClientMessageBridge.register();
			ClientTickEvents.END_CLIENT_TICK.register(RUNTIME::tick);
			ClientLifecycleEvents.CLIENT_STOPPING.register(RUNTIME::stop);
			ScreenEvents.AFTER_INIT.register((current, screen, width, height) -> {
				if (screen instanceof TitleScreen) {
					RUNTIME.titleShown(current);
					addMenuButtons(screen, ClientMenuButtons.TITLE, width);
				} else if (screen instanceof PauseScreen) {
					addMenuButtons(screen, ClientMenuButtons.PAUSE, width);
				}
			});
		} catch (Exception e) {
		    System.err.println("Ima crushogolic..");
			System.err.println(e);
			throw e;
		}
	}

	private static void addMenuButtons(Screen screen, String menu, int width) {
		int originY = screen.height / 4;
		int y = ClientScreenWidgets.bottomOfCenteredButtons(screen, originY + 48) + 4;
		for (ClientMenuButtons.Entry entry : RUNTIME.menuButtons().snapshot(menu)) {
			var owner = entry.plugin();
			String buttonId = entry.id();
			Button.Builder builder = Button.builder(Component.literal(entry.label()), clicked ->
					RUNTIME.menuButtonClicked(owner, buttonId, menu, Minecraft.getInstance())
			);
			if (entry.positioned()) {
				builder.bounds(width / 2 + entry.x(), originY + entry.y(), entry.width(), 20);
			} else {
				builder.bounds(width / 2 - 100, y, 200, 20);
				y += 24;
			}
			ClientScreenWidgets.add(screen, builder.build());
		}
	}
}
