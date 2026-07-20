package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.host.GoPluginManager;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.permissions.Permissions;

public final class BridgeAdminNetworking {
	private BridgeAdminNetworking() {
	}

	public static void register(GoPluginManager plugins) {
		PayloadTypeRegistry.serverboundPlay().register(AdminRequestPayload.TYPE, AdminRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(
				AdminResponsePayload.TYPE,
				AdminResponsePayload.CODEC,
				AdminResponsePayload.MAX_JSON_CHARS * 4 + 256
		);

		ServerPlayNetworking.registerGlobalReceiver(AdminRequestPayload.TYPE, (payload, context) -> {
			boolean allowed = context.player().permissions().hasPermission(Permissions.COMMANDS_ADMIN);
			if (!allowed) {
				send(context, plugins.managementSnapshot(false, "Administrator permission is required"));
				return;
			}

			String message = null;
			if ("reload".equals(payload.action())) {
				ReloadResult result = plugins.reload(payload.pluginId(), context.server());
				message = result.message();
			} else if (!"refresh".equals(payload.action())) {
				message = "Unknown admin action: " + payload.action();
			}

			send(context, plugins.managementSnapshot(true, message));
		});
	}

	private static void send(
			ServerPlayNetworking.Context context,
			BridgeManagementSnapshot snapshot
	) {
		String json = ProtocolJson.GSON.toJson(snapshot);
		context.responseSender().sendPacket(new AdminResponsePayload(json));
	}
}
