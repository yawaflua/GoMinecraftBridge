package dev.yawaflua.gominecraftbridge.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "GMB_TEST_LIBRARY", matches = ".+")
class PaperShadedNativeBackendTest {
	@Test
	void shadedPaperJarLoadsTheRealGoLibrary() throws Exception {
		URL jar = Path.of(System.getProperty("gmb.paper.shadowJar")).toUri().toURL();
		try (URLClassLoader loader = new URLClassLoader(
				new URL[]{jar}, ClassLoader.getPlatformClassLoader()
		)) {
			Class<?> backendType = loader.loadClass(
					"dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend"
			);
			Object backend = backendType.getConstructor(Path.class)
					.newInstance(Path.of(System.getenv("GMB_TEST_LIBRARY")));
			Class<?> pluginBackendType = loader.loadClass(
					"dev.yawaflua.gominecraftbridge.backend.PluginBackend"
			);
			Class<?> loadedPluginType = loader.loadClass(
					"dev.yawaflua.gominecraftbridge.host.LoadedPlugin"
			);
			Object plugin = loadedPluginType.getConstructor(pluginBackendType).newInstance(backend);
			Object metadata = loadedPluginType.getMethod("metadata").invoke(plugin);
			String id = (String) metadata.getClass().getMethod("id").invoke(metadata);

			assertEquals("hello_native", id);
		}
	}
}
