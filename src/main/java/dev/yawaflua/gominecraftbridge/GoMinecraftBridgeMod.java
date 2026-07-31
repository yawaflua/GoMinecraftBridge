package dev.yawaflua.gominecraftbridge;

import dev.yawaflua.gominecraftbridge.api.GoMinecraftBridgeApi;
import dev.yawaflua.gominecraftbridge.fabric.GbmFabricServerRuntime;
import dev.yawaflua.gominecraftbridge.host.BuiltInSystemCalls;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric bootstrap for GBM. */
public final class GoMinecraftBridgeMod implements ModInitializer {
	public static final String MOD_ID = "gbm";
	private static final Logger LOGGER = LoggerFactory.getLogger("gbm");

	@Override
	public void onInitialize() {
		BuiltInSystemCalls.register(GoMinecraftBridgeApi.systemCalls());
		new GbmFabricServerRuntime(LOGGER).register();
		LOGGER.info("GBM initialized");
	}
}
