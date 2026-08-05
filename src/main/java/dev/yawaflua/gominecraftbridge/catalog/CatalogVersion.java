package dev.yawaflua.gominecraftbridge.catalog;

import java.util.List;

/** Downloadable backend version and the metadata needed to validate its package. */
public record CatalogVersion(
		String id,
		String projectId,
		String version,
		String sha256,
		long sizeBytes,
		String tag,
		Metadata metadata
) {
	public CatalogVersion {
		id = value(id);
		projectId = value(projectId);
		version = value(version);
		sha256 = value(sha256);
		tag = value(tag);
		metadata = metadata == null ? new Metadata("", "", List.of(), List.of(), "", "", "") : metadata;
	}

	public record Metadata(
			String slug,
			String description,
			List<String> licenses,
			List<String> authors,
			String abiVersion,
			String apiVersion,
			String environment
	) {
		public Metadata {
			slug = value(slug);
			description = value(description);
			licenses = licenses == null ? List.of() : List.copyOf(licenses);
			authors = authors == null ? List.of() : List.copyOf(authors);
			abiVersion = value(abiVersion);
			apiVersion = value(apiVersion);
			environment = value(environment);
		}

		public boolean supportsClient() {
			return environment.equals("PLUGIN_ENVIRONMENT_CLIENT")
					|| environment.equals("PLUGIN_ENVIRONMENT_BOTH")
					|| environment.equalsIgnoreCase("client")
					|| environment.equalsIgnoreCase("both");
		}

		public boolean supportsServer() {
			return environment.equals("PLUGIN_ENVIRONMENT_SERVER")
					|| environment.equals("PLUGIN_ENVIRONMENT_BOTH")
					|| environment.equalsIgnoreCase("server")
					|| environment.equalsIgnoreCase("both");
		}

		public boolean supportsProtocol(int version) {
			String expected = Integer.toString(version);
			return abiVersion.equals(expected) && apiVersion.equals(expected);
		}
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
