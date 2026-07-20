package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeAdminNetworking1211Test {
	@Test
	void managementJsonIsTrimmedBelowTheLegacyPayloadLimit() {
		String hugeMessage = "Ж".repeat(8192);
		List<PluginLog> logs = java.util.stream.IntStream.range(0, 500)
				.mapToObj(index -> new PluginLog("sdk", "info", hugeMessage, index))
				.toList();
		PluginMetadata metadata = new PluginMetadata(
				"large", "Large", "1", "", List.of(), null, 2, null, PluginEnvironment.BOTH
		);
		BridgeManagementSnapshot snapshot = new BridgeManagementSnapshot(
				1, true, true, null, List.of(),
				List.of(new ManagedPluginSnapshot(metadata, "running", "native", "/plugin.so", logs))
		);

		String json = BridgeAdminNetworking.boundedJson(snapshot);

		assertTrue(json.length() <= AdminResponsePayload.MAX_JSON_CHARS);
		assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= AdminResponsePayload.MAX_JSON_CHARS);
	}
}
