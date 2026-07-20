package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonParseException;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.network.AdminRequestPayload;
import dev.yawaflua.gominecraftbridge.network.AdminResponsePayload;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GoMinecraftBridgeClient implements ClientModInitializer {
	private static BridgeManagementSnapshot latest;
	private static Runnable updateListener;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(AdminResponsePayload.TYPE, (payload, context) -> {
			try {
				latest = ProtocolJson.GSON.fromJson(payload.json(), BridgeManagementSnapshot.class);
			} catch (JsonParseException exception) {
				latest = new BridgeManagementSnapshot(
						System.currentTimeMillis(), false, false,
						"Invalid management response: " + exception.getMessage(), null, null
				);
			}
			if (updateListener != null) {
				updateListener.run();
			}
		});
	}

	public static BridgeManagementSnapshot latest() {
		return latest;
	}

	public static void onUpdate(Runnable listener) {
		updateListener = listener;
	}

	public static boolean requestRefresh() {
		return send("refresh", "");
	}

	public static boolean requestReload(String pluginId) {
		return send("reload", pluginId);
	}

	public static boolean requestRescan() {
		return send("rescan", "");
	}

	private static boolean send(String action, String pluginId) {
		if (!ClientPlayNetworking.canSend(AdminRequestPayload.TYPE)) {
			return false;
		}
		ClientPlayNetworking.send(new AdminRequestPayload(action, pluginId));
		return true;
	}
}
