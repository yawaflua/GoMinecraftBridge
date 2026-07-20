package dev.yawaflua.gominecraftbridge.protocol;

public record InitEvent(
		String minecraftVersion,
		boolean dedicated,
		String dataDirectory,
		PluginEnvironment runtimeEnvironment
) {
	/** Source-compatible constructor for server hosts using the original ABI v2 event. */
	public InitEvent(String minecraftVersion, boolean dedicated, String dataDirectory) {
		this(minecraftVersion, dedicated, dataDirectory, PluginEnvironment.SERVER);
	}
}
