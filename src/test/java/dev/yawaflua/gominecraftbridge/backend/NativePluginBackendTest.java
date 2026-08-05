package dev.yawaflua.gominecraftbridge.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.host.LoadedPlugin;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.ActionResult;
import dev.yawaflua.gominecraftbridge.protocol.BlockSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.ClientTickEvent;
import dev.yawaflua.gominecraftbridge.protocol.ConfigUpdateEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeinitEvent;
import dev.yawaflua.gominecraftbridge.protocol.PluginResponse;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.LevelSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.InteractionEvent;
import dev.yawaflua.gominecraftbridge.protocol.EntitySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "GBM_TEST_LIBRARY", matches = ".+")
final class NativePluginBackendTest {
	@Test
	void loadsMetadataAndDispatchesChat() {
		Path library = Path.of(System.getenv("GBM_TEST_LIBRARY"));
		LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(library));

		assertEquals("hello_native", plugin.metadata().id());
		assertEquals(Protocol.ABI_VERSION, plugin.metadata().apiVersion());
		assertEquals(PluginEnvironment.BOTH, plugin.metadata().environment());
		assertEquals(true, plugin.metadata().configWritable());

		JsonObject config = new JsonObject();
		config.addProperty("greeting", "Configured through Java");
		config.addProperty("enabled", true);
		config.addProperty("repeatTicks", 600);
		config.add("favoriteTags", new JsonArray());
		PluginResponse configUpdate = plugin.invoke(
				Protocol.Operation.CONFIG_UPDATE,
				new ConfigUpdateEvent(config)
		);
		assertEquals("ok", configUpdate.status());
		assertEquals(
				"Configured through Java",
				configUpdate.data().getAsJsonObject().get("greeting").getAsString()
		);

		PluginResponse response = plugin.invoke(
				Protocol.Operation.CHAT,
				new ChatEvent("00000000-0000-0000-0000-000000000001", "Test", "!go", 1)
		);
		assertEquals("ok", response.status());
		assertEquals(1, response.actions().size());
		assertEquals("minecraft:chat.player", response.actions().getFirst().type());
		assertEquals(true, !response.actions().getFirst().id().isBlank());
		assertEquals(1, response.systemCalls().size());
		assertEquals("minecraft:server.info", response.systemCalls().getFirst().name());

		PluginResponse rejectedAction = plugin.invoke(
				Protocol.Operation.ACTION_RESULT,
				new ActionResult(
						response.actions().getFirst().id(),
						response.actions().getFirst().type(),
						false,
						"test rejection"
				)
		);
		assertEquals("ok", rejectedAction.status());
		assertEquals(true, rejectedAction.logs().getFirst().message().contains("test rejection"));

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

		EntitySnapshot interactingPlayer = new EntitySnapshot(
				7, "00000000-0000-0000-0000-000000000007", "minecraft:player", "Clicker",
				"minecraft:overworld", 1, 64, 1, 0, 0, 0, 0, 0, true, true, 20F, 20F
		);
		PluginResponse interaction = plugin.invoke(
				Protocol.Operation.INTERACTION,
				new InteractionEvent(
						"use_block", "main_hand", true, interactingPlayer,
						new BlockSnapshot("minecraft:overworld", 1, 64, 2, "minecraft:oak_sign", Map.of("rotation", "0")),
						null, "north", 1.5, 64.5, 2.5, 1
				)
		);
		assertEquals("ok", interaction.status());
		assertEquals(true, interaction.logs().getFirst().message().contains("shift-clicked sign minecraft:oak_sign"));

		ByteBuffer capture = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
		capture.put(new byte[]{'G', 'M', 'B', 'C', 1, 1, 0, 0});
		capture.putInt(2).putInt(1).putInt(8).putInt(8);
		capture.put(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
		PluginResponse captured = plugin.invokeRaw(Protocol.Operation.CLIENT_SCREEN_CAPTURE, capture.array());
		assertEquals("ok", captured.status());
		assertEquals("captured screen: 2x1 stride=8 bytes=8", captured.logs().getFirst().message());

		PluginResponse deinit = plugin.invoke(
				Protocol.Operation.DEINIT,
				new DeinitEvent("integration_test")
		);
		assertEquals(1, deinit.logs().size());
		assertEquals("deinit: integration_test", deinit.logs().getFirst().message());
	}
}
