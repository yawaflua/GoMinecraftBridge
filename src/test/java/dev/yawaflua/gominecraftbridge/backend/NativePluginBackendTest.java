package dev.yawaflua.gominecraftbridge.backend;

import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "GMB_TEST_LIBRARY", matches = ".+")
final class NativePluginBackendTest {
	@Test
	void loadsMetadataAndDispatchesChat() {
		Path library = Path.of(System.getenv("GMB_TEST_LIBRARY"));
		LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(library));

		assertEquals("hello_native", plugin.metadata().id());
		assertEquals(Protocol.ABI_VERSION, plugin.metadata().apiVersion());

		PluginResponse response = plugin.invoke(
				Protocol.Operation.CHAT,
				new ChatEvent("00000000-0000-0000-0000-000000000001", "Test", "!go", 1)
		);
		assertEquals("ok", response.status());
		assertEquals(1, response.actions().size());
		assertEquals("minecraft:chat.player", response.actions().getFirst().type());
		assertEquals(1, response.systemCalls().size());
		assertEquals("minecraft:server.info", response.systemCalls().getFirst().name());

		PluginResponse deinit = plugin.invoke(
				Protocol.Operation.DEINIT,
				new DeinitEvent("integration_test")
		);
		assertEquals(1, deinit.logs().size());
		assertEquals("deinit: integration_test", deinit.logs().getFirst().message());
	}
}
