package dev.yawaflua.gominecraftbridge.api;

public final class GoMinecraftBridgeApi {
	private static final SystemCallRegistry SYSTEM_CALLS = new SystemCallRegistry();
	private static final GoPluginRegistry PLUGINS = new GoPluginRegistry();

	private GoMinecraftBridgeApi() {
	}

	public static SystemCallRegistry systemCalls() {
		return SYSTEM_CALLS;
	}

	public static GoPluginRegistry plugins() {
		return PLUGINS;
	}
}
