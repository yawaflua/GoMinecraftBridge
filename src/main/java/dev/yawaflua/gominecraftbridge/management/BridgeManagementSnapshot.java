package dev.yawaflua.gominecraftbridge.management;

import java.util.List;

public record BridgeManagementSnapshot(
		long generatedAtUnixMilli,
		boolean serverRunning,
		boolean canReload,
		String message,
		List<PackageInspection> packages,
		List<ManagedPluginSnapshot> plugins
) {
	public BridgeManagementSnapshot {
		packages = packages == null ? List.of() : List.copyOf(packages);
		plugins = plugins == null ? List.of() : List.copyOf(plugins);
	}
}
