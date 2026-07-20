package dev.yawaflua.gominecraftbridge.backend;

import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.ClientTickEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.LevelSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.EntitySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "GMB_TEST_LIBRARY", matches = ".+")
final class NativePluginBackendTest {
	@Test
	void loadsMetadataAndDispatchesChat() {
		Path library = Path.of(System.getenv("GMB_TEST_LIBRARY"));
		LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(library));

		assertEquals("hello_native", plugin.metadata().id());
		assertEquals(Protocol.ABI_VERSION, plugin.metadata().apiVersion());
		assertEquals(PluginEnvironment.BOTH, plugin.metadata().environment());

		PluginResponse response = plugin.invoke(
				Protocol.Operation.CHAT,
				new ChatEvent("00000000-0000-0000-0000-000000000001", "Test", "!go", 1)
		);
		assertEquals("ok", response.status());
		assertEquals(1, response.actions().size());
		assertEquals("minecraft:chat.player", response.actions().getFirst().type());
		assertEquals(1, response.systemCalls().size());
		assertEquals("minecraft:server.info", response.systemCalls().getFirst().name());

		PluginResponse tick = plugin.invoke(
				Protocol.Operation.TICK,
				new ServerSnapshot(
						200,
						1,
						List.of(new LevelSnapshot("minecraft:overworld", 200, 200, false, false)),
						List.of(new EntitySnapshot(
								1,
								"00000000-0000-0000-0000-000000000001",
								"minecraft:player",
								"Test",
								"minecraft:overworld",
								1, 64, 1, 0, 0, 0, 0, 0, true, true, 20F, 20F
						)),
						List.of()
				)
		);
		assertEquals("ok", tick.status());
		assertEquals("tick=200 entities=1 watched_blocks=0", tick.logs().getFirst().message());

		PluginResponse clientTick = plugin.invoke(
				Protocol.Operation.CLIENT_TICK,
				new ClientTickEvent(1200, 1, true, "localhost", "uuid", "Test", "minecraft:overworld")
		);
		assertEquals("ok", clientTick.status());
		assertEquals("minecraft:client.chat.display", clientTick.actions().getFirst().type());

		PluginResponse deinit = plugin.invoke(
				Protocol.Operation.DEINIT,
				new DeinitEvent("integration_test")
		);
		assertEquals(1, deinit.logs().size());
		assertEquals("deinit: integration_test", deinit.logs().getFirst().message());
	}
}
