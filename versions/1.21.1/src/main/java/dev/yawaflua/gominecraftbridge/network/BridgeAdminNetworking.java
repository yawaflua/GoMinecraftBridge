package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.host.GoPluginManager;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minecraft 1.21.1 networking registration overlay. */
public final class BridgeAdminNetworking {
	private BridgeAdminNetworking() {
	}

	public static void register(GoPluginManager plugins) {
		PayloadTypeRegistry.playC2S().register(AdminRequestPayload.TYPE, AdminRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AdminResponsePayload.TYPE, AdminResponsePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(AdminRequestPayload.TYPE, (payload, context) -> {
			boolean allowed = MinecraftVersionAdapter.isOperator(context.server(), context.player());
			if (!allowed) {
				send(context, withoutDetails(plugins.managementSnapshot(
						false, "Administrator permission is required"
				)));
				return;
			}

			String message = null;
			if ("reload".equals(payload.action())) {
				ReloadResult result = plugins.reload(payload.pluginId(), context.server());
				message = result.message();
			} else if ("rescan".equals(payload.action())) {
				ReloadResult result = plugins.rescan(context.server());
				message = result.message();
			} else if (!"refresh".equals(payload.action())) {
				message = "Unknown admin action: " + payload.action();
			}

			send(context, plugins.managementSnapshot(true, message));
		});
	}

	static BridgeManagementSnapshot withoutDetails(BridgeManagementSnapshot snapshot) {
		return new BridgeManagementSnapshot(
				snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), false,
				snapshot.message(), List.of(), List.of()
		);
	}

	private static void send(ServerPlayNetworking.Context context, BridgeManagementSnapshot snapshot) {
		context.responseSender().sendPacket(new AdminResponsePayload(boundedJson(snapshot)));
	}

	/** Keeps the encoded 1.21.1 payload below its fixed 1 MiB protocol limit. */
	static String boundedJson(BridgeManagementSnapshot snapshot) {
		String json = ProtocolJson.GSON.toJson(snapshot);
		if (fits(json)) {
			return json;
		}

		for (int retainedLogs : new int[]{64, 16, 4, 0}) {
			List<ManagedPluginSnapshot> plugins = new ArrayList<>(snapshot.plugins().size());
			for (ManagedPluginSnapshot plugin : snapshot.plugins()) {
				int from = Math.max(0, plugin.logs().size() - retainedLogs);
				plugins.add(new ManagedPluginSnapshot(
						plugin.metadata(), plugin.state(), plugin.backend(), plugin.origin(),
						plugin.logs().subList(from, plugin.logs().size())
				));
			}
			BridgeManagementSnapshot trimmed = new BridgeManagementSnapshot(
					snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), snapshot.canReload(),
					"Response was shortened to fit the Minecraft 1.21.1 payload limit",
					snapshot.packages(), plugins
			);
			json = ProtocolJson.GSON.toJson(trimmed);
			if (fits(json)) {
				return json;
			}
		}

		return ProtocolJson.GSON.toJson(new BridgeManagementSnapshot(
				snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), snapshot.canReload(),
				"Management data exceeded the Minecraft 1.21.1 payload limit; reload or inspect fewer packages",
				List.of(), List.of()
		));
	}

	private static boolean fits(String json) {
		return json.length() <= AdminResponsePayload.MAX_JSON_CHARS
				&& json.getBytes(StandardCharsets.UTF_8).length <= AdminResponsePayload.MAX_JSON_CHARS;
	}
}
