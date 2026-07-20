package dev.yawaflua.gominecraftbridge.host;

import dev.yawaflua.gominecraftbridge.backend.PluginBackend;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.Protocol;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LoadedPluginMetadataTest {
	@Test
	void defaultsLegacyMetadataToServer() {
		LoadedPlugin plugin = new LoadedPlugin(new MetadataBackend(""));

		assertEquals(PluginEnvironment.SERVER, plugin.metadata().environment());
	}

	@Test
	void acceptsAllDeclaredEnvironments() {
		assertEquals(
				PluginEnvironment.SERVER,
				new LoadedPlugin(new MetadataBackend(",\"environment\":\"server\"")).metadata().environment()
		);
		assertEquals(
				PluginEnvironment.CLIENT,
				new LoadedPlugin(new MetadataBackend(",\"environment\":\"client\"")).metadata().environment()
		);
		assertEquals(
				PluginEnvironment.BOTH,
				new LoadedPlugin(new MetadataBackend(",\"environment\":\"both\"")).metadata().environment()
		);
	}

	@Test
	void rejectsUnknownEnvironment() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new LoadedPlugin(new MetadataBackend(",\"environment\":\"proxy\""))
		);
	}

	private record MetadataBackend(String environmentField) implements PluginBackend {
		@Override
		public int abiVersion() {
			return Protocol.ABI_VERSION;
		}

		@Override
		public byte[] call(Protocol.Operation operation, byte[] input) {
			String json = "{\"status\":\"ok\",\"data\":{"
					+ "\"id\":\"test_plugin\",\"name\":\"Test\",\"version\":\"1.0.0\",\"apiVersion\":2"
					+ environmentField + "}}";
			return json.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public Path origin() {
			return Path.of("test-plugin");
		}
	}
}
