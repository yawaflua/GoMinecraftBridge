package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record InitEvent(
		String minecraftVersion,
		boolean dedicated,
		String dataDirectory,
		PluginEnvironment runtimeEnvironment,
		List<String> capabilities
) {
	public InitEvent {
		capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
	}

	/** Convenience constructor for a server host without optional capabilities. */
	public InitEvent(String minecraftVersion, boolean dedicated, String dataDirectory) {
		this(minecraftVersion, dedicated, dataDirectory, PluginEnvironment.SERVER, List.of());
	}

	public InitEvent(
			String minecraftVersion,
			boolean dedicated,
			String dataDirectory,
			PluginEnvironment runtimeEnvironment
	) {
		this(minecraftVersion, dedicated, dataDirectory, runtimeEnvironment, List.of());
	}
}
