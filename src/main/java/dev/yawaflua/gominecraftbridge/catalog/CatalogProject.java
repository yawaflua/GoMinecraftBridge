package dev.yawaflua.gominecraftbridge.catalog;

/** A published project returned by the backend search endpoint. */
public record CatalogProject(
		String id,
		String slug,
		String name,
		String description,
		String latestVersion,
		float similarity
) {
	public CatalogProject {
		id = value(id);
		slug = value(slug);
		name = value(name);
		description = value(description);
		latestVersion = value(latestVersion);
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
