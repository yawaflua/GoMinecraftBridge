package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonParseException;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.network.AdminRequestPayload;
import dev.yawaflua.gominecraftbridge.network.AdminResponsePayload;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.LoggerFactory;

public final class GoMinecraftBridgeClient implements ClientModInitializer {
	public enum ConnectionStatus {
		DISCONNECTED,
		CONNECTING,
		AVAILABLE,
		UNSUPPORTED
	}

	private static volatile BridgeManagementSnapshot latest;
	private static final ClientGoPluginManager CLIENT_PLUGINS = new ClientGoPluginManager(
			LoggerFactory.getLogger("go_minecraft_bridge/client")
	);
	private static volatile ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
	private static Runnable updateListener;

	@Override
	public void onInitializeClient() {
		CLIENT_PLUGINS.discover();
		ClientLifecycleEvents.CLIENT_STARTED.register(CLIENT_PLUGINS::start);
		ClientTickEvents.END_CLIENT_TICK.register(CLIENT_PLUGINS::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(CLIENT_PLUGINS::stop);

		ClientPlayNetworking.registerGlobalReceiver(AdminResponsePayload.TYPE, (payload, context) -> {
			try {
				latest = ProtocolJson.GSON.fromJson(payload.json(), BridgeManagementSnapshot.class);
			} catch (JsonParseException exception) {
				latest = new BridgeManagementSnapshot(
						System.currentTimeMillis(), false, false,
						"Invalid management response: " + exception.getMessage(), null, null
				);
			}
			connectionStatus = ConnectionStatus.AVAILABLE;
			notifyUpdate();
		});

		ClientPlayConnectionEvents.INIT.register((listener, client) -> reset(ConnectionStatus.CONNECTING));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			latest = null;
			connectionStatus = ClientPlayNetworking.canSend(AdminRequestPayload.TYPE)
					? ConnectionStatus.AVAILABLE
					: ConnectionStatus.UNSUPPORTED;
			notifyUpdate();
			if (connectionStatus == ConnectionStatus.AVAILABLE) {
				requestRefresh();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
				reset(ConnectionStatus.DISCONNECTED)
		);
	}

	public static BridgeManagementSnapshot latest() {
		return latest;
	}

	public static BridgeManagementSnapshot localPlugins() {
		return CLIENT_PLUGINS.managementSnapshot("Client native runtime");
	}

	public static void requestLocalRescan() {
		CLIENT_PLUGINS.rescan();
		notifyUpdate();
	}

	public static void requestLocalReload(String pluginId) {
		CLIENT_PLUGINS.reload(pluginId, net.minecraft.client.Minecraft.getInstance());
		notifyUpdate();
	}

	public static ConnectionStatus connectionStatus() {
		return connectionStatus;
	}

	public static boolean channelAvailable() {
		return connectionStatus == ConnectionStatus.AVAILABLE;
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
			if (connectionStatus != ConnectionStatus.DISCONNECTED) {
				connectionStatus = ConnectionStatus.UNSUPPORTED;
			}
			return false;
		}
		ClientPlayNetworking.send(new AdminRequestPayload(action, pluginId));
		return true;
	}

	private static void reset(ConnectionStatus status) {
		latest = null;
		connectionStatus = status;
		notifyUpdate();
	}

	private static void notifyUpdate() {
		Runnable listener = updateListener;
		if (listener != null) {
			listener.run();
		}
	}
}
