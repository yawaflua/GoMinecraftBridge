package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAdminMessagingTest {
	@Test
	void requestCodecMatchesMinecraftVarIntUtfFormat() {
		byte[] encoded = PaperAdminMessaging.encodeRequest("reload", "пример");

		assertEquals(
				new PaperAdminMessaging.AdminRequest("reload", "пример"),
				PaperAdminMessaging.decodeRequest(encoded)
		);
		assertThrows(IllegalArgumentException.class, () ->
				PaperAdminMessaging.decodeRequest(new byte[]{1, 'x', 0, 1}));
	}

	@Test
	void responseCodecRoundTripsUnicode() {
		String input = "Привет from Paper";
		assertEquals(input, PaperAdminMessaging.decodeString(PaperAdminMessaging.encodeString(input)));
	}

	@Test
	void managementResponseIsTrimmedBelowBukkitMessageLimit() {
		PluginMetadata metadata = new PluginMetadata(
				"large", "Large", "1", "", List.of(), null, 2, null, PluginEnvironment.SERVER
		);
		List<PluginLog> logs = java.util.stream.IntStream.range(0, 500)
				.mapToObj(index -> new PluginLog("sdk", "info", "Ж".repeat(8192), index))
				.toList();
		BridgeManagementSnapshot snapshot = new BridgeManagementSnapshot(
				1, true, true, null, List.of(),
				List.of(new ManagedPluginSnapshot(metadata, "running", "native", "/plugin.so", logs))
		);

		String json = PaperAdminMessaging.boundedJson(snapshot);

		assertTrue(json.getBytes(StandardCharsets.UTF_8).length + 3 <= 30_000);
	}
}
