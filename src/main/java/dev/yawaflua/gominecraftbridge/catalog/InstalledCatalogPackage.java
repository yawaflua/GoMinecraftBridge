package dev.yawaflua.gominecraftbridge.catalog;

/** Durable source metadata for a package installed from the GBM backend. */
public record InstalledCatalogPackage(
		String projectId,
		String slug,
		String pluginId,
		String version,
		String downloadUrl,
		String sha256,
		String binaryPath,
		long installedAtUnixMilli
) {
	public InstalledCatalogPackage {
		projectId = value(projectId);
		slug = value(slug);
		pluginId = value(pluginId);
		version = value(version);
		downloadUrl = value(downloadUrl);
		sha256 = value(sha256);
		binaryPath = value(binaryPath);
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
