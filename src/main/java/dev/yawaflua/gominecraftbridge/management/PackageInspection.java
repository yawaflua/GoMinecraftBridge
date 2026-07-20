package dev.yawaflua.gominecraftbridge.management;

public record PackageInspection(
		String path,
		boolean valid,
		String pluginId,
		String error
) {
}
