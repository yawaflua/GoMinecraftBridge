package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.annotations.SerializedName;

/** Declares the Minecraft process in which a Go plugin is allowed to run. */
public enum PluginEnvironment {
	@SerializedName("server")
	SERVER,
	@SerializedName("client")
	CLIENT,
	@SerializedName("both")
	BOTH;

	public boolean supportsServer() {
		return this == SERVER || this == BOTH;
	}

	public boolean supportsClient() {
		return this == CLIENT || this == BOTH;
	}
}
