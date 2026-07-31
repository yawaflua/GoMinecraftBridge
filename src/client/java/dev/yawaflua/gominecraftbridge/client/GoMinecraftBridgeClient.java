package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
import dev.yawaflua.gominecraftbridge.network.AdminResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Fabric bootstrap. All GBM client behavior lives in {@link GbmClientRuntime}. */
public final class GoMinecraftBridgeClient implements ClientModInitializer {
	private static final GbmClientRuntime RUNTIME = new GbmClientRuntime();

	public static GbmClientRuntime runtime() {
		return RUNTIME;
	}

	@Override
	public void onInitializeClient() {
		Minecraft client = Minecraft.getInstance();
		RUNTIME.start(client);
		ClientHudRendering.register(RUNTIME.hud());
		ClientTickEvents.END_CLIENT_TICK.register(RUNTIME::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(RUNTIME::stop);

		ClientPlayNetworking.registerGlobalReceiver(AdminResponsePayload.TYPE, (payload, context) ->
				RUNTIME.receiveRemoteSnapshot(payload.json())
		);
		ClientPlayConnectionEvents.INIT.register((listener, game) -> RUNTIME.connecting());
		ClientPlayConnectionEvents.JOIN.register((listener, sender, game) -> RUNTIME.joinedServer());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, game) -> RUNTIME.disconnected());
	}
}
