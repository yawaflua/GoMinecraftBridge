package dev.yawaflua.gominecraftbridge.host;

import dev.yawaflua.gominecraftbridge.backend.PluginBackend;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import dev.yawaflua.gominecraftbridge.protocol.SnapshotSubscription;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.TickSnapshotFlatBuffer;

import java.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class LoadedPlugin {
	private static final int MAX_RETAINED_LOGS = 500;

	private final PluginBackend backend;
	private final PluginMetadata metadata;
	private final ArrayDeque<PluginLog> logs = new ArrayDeque<>();
	private PluginState state = PluginState.DISCOVERED;
	private SnapshotSubscription snapshotSubscription = SnapshotSubscription.defaults();

	public LoadedPlugin(PluginBackend backend) {
		this.backend = Objects.requireNonNull(backend, "backend");

		if (backend.abiVersion() != Protocol.ABI_VERSION) {
			throw new IllegalArgumentException(
					"Unsupported ABI " + backend.abiVersion() + " in " + backend.origin()
			);
		}

		PluginResponse response = invokeUnchecked(Protocol.Operation.METADATA, null);
		if (response.isError() || response.data() == null) {
			throw new IllegalArgumentException("Cannot read plugin metadata: " + response.error());
		}

		validateEnvironment(response.data());
		this.metadata = ProtocolJson.GSON.fromJson(response.data(), PluginMetadata.class);
		validateMetadata(this.metadata);
	}

	public synchronized PluginResponse invoke(Protocol.Operation operation, Object input) {
		if (this.state == PluginState.DISABLED || this.state == PluginState.STOPPED) {
			throw new IllegalStateException("Plugin " + this.metadata.id() + " is not active");
		}

		return invokeUnchecked(operation, input);
	}

	private PluginResponse invokeUnchecked(Protocol.Operation operation, Object input) {
		byte[] encodedInput;
		if (operation == Protocol.Operation.TICK && input instanceof ServerSnapshot snapshot) {
			encodedInput = TickSnapshotFlatBuffer.encode(snapshot);
		} else {
			encodedInput = ProtocolJson.encode(input);
		}
		byte[] output = this.backend.call(operation, encodedInput);
		if (output.length == 0) {
			throw new IllegalArgumentException("Plugin returned an empty response for " + operation);
		}

		return ProtocolJson.decode(output, PluginResponse.class);
	}

	public PluginBackend backend() {
		return this.backend;
	}

	public PluginMetadata metadata() {
		return this.metadata;
	}

	public synchronized PluginState state() {
		return this.state;
	}

	public synchronized void markRunning() {
		this.state = PluginState.RUNNING;
	}

	public synchronized void disable() {
		this.state = PluginState.DISABLED;
	}

	public synchronized void markStopped() {
		this.state = PluginState.STOPPED;
	}

	public synchronized void prepareReload() {
		this.state = PluginState.DISCOVERED;
		this.snapshotSubscription = SnapshotSubscription.defaults();
	}

	public synchronized SnapshotSubscription snapshotSubscription() {
		return this.snapshotSubscription;
	}

	public synchronized void snapshotSubscription(SnapshotSubscription snapshotSubscription) {
		this.snapshotSubscription = snapshotSubscription;
	}

	public synchronized void appendLog(PluginLog log) {
		String message = log.message() == null ? "" : log.message();
		if (message.length() > 8_192) {
			message = message.substring(0, 8_192) + "… [truncated]";
		}
		log = new PluginLog(log.stream(), log.level(), message, log.timestampUnixMilli());
		while (this.logs.size() >= MAX_RETAINED_LOGS) {
			this.logs.removeFirst();
		}
		this.logs.addLast(log);
	}

	public synchronized List<PluginLog> logs() {
		return List.copyOf(new ArrayList<>(this.logs));
	}

	private static void validateMetadata(PluginMetadata metadata) {
		if (metadata == null) {
			throw new IllegalArgumentException("Plugin metadata is null");
		}
		if (metadata.id() == null || !metadata.id().matches("[a-z][a-z0-9_-]{1,63}")) {
			throw new IllegalArgumentException("Invalid plugin id: " + metadata.id());
		}
		if (metadata.name() == null || metadata.name().isBlank()) {
			throw new IllegalArgumentException("Plugin name is required");
		}
		if (metadata.version() == null || metadata.version().isBlank()) {
			throw new IllegalArgumentException("Plugin version is required");
		}
		if (metadata.apiVersion() != Protocol.ABI_VERSION) {
			throw new IllegalArgumentException("Unsupported plugin API version " + metadata.apiVersion());
		}
	}

	private static void validateEnvironment(JsonElement rawMetadata) {
		if (!rawMetadata.isJsonObject()) {
			throw new IllegalArgumentException("Plugin metadata must be a JSON object");
		}
		JsonObject object = rawMetadata.getAsJsonObject();
		if (!object.has("environment") || object.get("environment").isJsonNull()) {
			return;
		}
		JsonElement value = object.get("environment");
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("Plugin environment must be server, client, or both");
		}
		String environment = value.getAsString();
		if (!environment.equals("server") && !environment.equals("client") && !environment.equals("both")) {
			throw new IllegalArgumentException("Invalid plugin environment: " + environment);
		}
	}
}
