package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fabric custom-payload compatible management bridge exposed by Paper/Purpur. */
final class PaperAdminMessaging implements PluginMessageListener {
	static final String REQUEST_CHANNEL = "go_minecraft_bridge:admin_request";
	static final String RESPONSE_CHANNEL = "go_minecraft_bridge:admin_response";
	private static final int MAX_PLUGIN_MESSAGE_BYTES = 30_000;

	private final Plugin owner;
	private final PaperGoPluginManager plugins;
	private final Logger logger;

	PaperAdminMessaging(Plugin owner, PaperGoPluginManager plugins) {
		this.owner = owner;
		this.plugins = plugins;
		this.logger = owner.getLogger();
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!REQUEST_CHANNEL.equals(channel)) {
			return;
		}
		try {
			AdminRequest request = decodeRequest(message);
			if (!player.isOp()) {
				send(player, withoutDetails(this.plugins.managementSnapshot(
						"Administrator permission is required"
				)));
				return;
			}

			String responseMessage = null;
			if ("reload".equals(request.action())) {
				ReloadResult result = this.plugins.reload(request.pluginId());
				responseMessage = result.message();
			} else if ("rescan".equals(request.action())) {
				ReloadResult result = this.plugins.rescan();
				responseMessage = result.message();
			} else if (!"refresh".equals(request.action())) {
				responseMessage = "Unknown admin action: " + request.action();
			}
			send(player, this.plugins.managementSnapshot(responseMessage));
		} catch (RuntimeException exception) {
			this.logger.log(Level.WARNING,
					"Rejected malformed Go Minecraft Bridge management payload from " + player.getName(), exception);
		}
	}

	private void send(Player player, BridgeManagementSnapshot snapshot) {
		player.sendPluginMessage(this.owner, RESPONSE_CHANNEL, encodeString(boundedJson(snapshot)));
	}

	static AdminRequest decodeRequest(byte[] payload) {
		Cursor cursor = new Cursor(payload);
		String action = cursor.readString(32);
		String pluginId = cursor.readString(64);
		if (!cursor.atEnd()) {
			throw new IllegalArgumentException("Trailing bytes in admin request");
		}
		return new AdminRequest(action, pluginId);
	}

	static byte[] encodeRequest(String action, String pluginId) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		writeString(output, action, 32);
		writeString(output, pluginId, 64);
		return output.toByteArray();
	}

	static byte[] encodeString(String value) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		writeString(output, value, MAX_PLUGIN_MESSAGE_BYTES);
		return output.toByteArray();
	}

	static String decodeString(byte[] payload) {
		Cursor cursor = new Cursor(payload);
		String value = cursor.readString(MAX_PLUGIN_MESSAGE_BYTES);
		if (!cursor.atEnd()) {
			throw new IllegalArgumentException("Trailing bytes after string payload");
		}
		return value;
	}

	static String boundedJson(BridgeManagementSnapshot snapshot) {
		String json = ProtocolJson.GSON.toJson(snapshot);
		if (fits(json)) {
			return json;
		}
		for (int retainedLogs : new int[]{20, 5, 0}) {
			List<ManagedPluginSnapshot> trimmedPlugins = new ArrayList<>();
			for (ManagedPluginSnapshot plugin : snapshot.plugins()) {
				int from = Math.max(0, plugin.logs().size() - retainedLogs);
				trimmedPlugins.add(new ManagedPluginSnapshot(
						plugin.metadata(), plugin.state(), plugin.backend(), plugin.origin(),
						plugin.logs().subList(from, plugin.logs().size())
				));
			}
			json = ProtocolJson.GSON.toJson(new BridgeManagementSnapshot(
					snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), snapshot.canReload(),
					"Response was shortened to fit the Paper plugin-message limit",
					snapshot.packages(), trimmedPlugins
			));
			if (fits(json)) {
				return json;
			}
		}
		return ProtocolJson.GSON.toJson(new BridgeManagementSnapshot(
				snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), snapshot.canReload(),
				"Management data exceeded the Paper plugin-message limit; use /gmb for full details",
				List.of(), List.of()
		));
	}

	private static BridgeManagementSnapshot withoutDetails(BridgeManagementSnapshot snapshot) {
		return new BridgeManagementSnapshot(
				snapshot.generatedAtUnixMilli(), snapshot.serverRunning(), false,
				snapshot.message(), List.of(), List.of()
		);
	}

	private static boolean fits(String json) {
		return json.getBytes(StandardCharsets.UTF_8).length + 3 <= MAX_PLUGIN_MESSAGE_BYTES;
	}

	private static void writeString(ByteArrayOutputStream output, String value, int maxChars) {
		String safe = value == null ? "" : value;
		if (safe.length() > maxChars) {
			throw new IllegalArgumentException("String exceeds " + maxChars + " characters");
		}
		byte[] encoded = safe.getBytes(StandardCharsets.UTF_8);
		writeVarInt(output, encoded.length);
		output.writeBytes(encoded);
	}

	private static void writeVarInt(ByteArrayOutputStream output, int value) {
		while ((value & -128) != 0) {
			output.write(value & 127 | 128);
			value >>>= 7;
		}
		output.write(value);
	}

	record AdminRequest(String action, String pluginId) {
	}

	private static final class Cursor {
		private final byte[] bytes;
		private int offset;

		private Cursor(byte[] bytes) {
			this.bytes = bytes;
		}

		private String readString(int maxChars) {
			int byteLength = readVarInt();
			if (byteLength < 0 || byteLength > maxChars * 4 || this.offset + byteLength > this.bytes.length) {
				throw new IllegalArgumentException("Invalid UTF-8 byte length " + byteLength);
			}
			String value = new String(this.bytes, this.offset, byteLength, StandardCharsets.UTF_8);
			this.offset += byteLength;
			if (value.length() > maxChars) {
				throw new IllegalArgumentException("Decoded string exceeds " + maxChars + " characters");
			}
			return value;
		}

		private int readVarInt() {
			int result = 0;
			for (int position = 0; position < 35; position += 7) {
				if (this.offset >= this.bytes.length) {
					throw new IllegalArgumentException("Truncated VarInt");
				}
				int current = this.bytes[this.offset++] & 0xFF;
				result |= (current & 0x7F) << position;
				if ((current & 0x80) == 0) {
					return result;
				}
			}
			throw new IllegalArgumentException("VarInt is too large");
		}

		private boolean atEnd() {
			return this.offset == this.bytes.length;
		}
	}
}
