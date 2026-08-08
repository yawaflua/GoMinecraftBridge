package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
import dev.yawaflua.gominecraftbridge.fabric.GbmFabricInteractionAdapter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

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
			ClientTickEvents.END_CLIENT_TICK.register(RUNTIME::tick);
			ClientLifecycleEvents.CLIENT_STOPPING.register(RUNTIME::stop);
		} catch (Exception e) {
		    System.err.println("Ima crushogolic..");
			System.err.println(e);
			throw e;
		}
	}
}
