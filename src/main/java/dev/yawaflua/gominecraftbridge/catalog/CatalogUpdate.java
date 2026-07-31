package dev.yawaflua.gominecraftbridge.catalog;

/** Result of comparing one locally installed package with the backend. */
public record CatalogUpdate(
		String projectId,
		String currentVersion,
		String latestVersion,
		boolean updateAvailable
) {
	public CatalogUpdate {
		projectId = projectId == null ? "" : projectId;
		currentVersion = currentVersion == null ? "" : currentVersion;
		latestVersion = latestVersion == null ? "" : latestVersion;
	}
}
