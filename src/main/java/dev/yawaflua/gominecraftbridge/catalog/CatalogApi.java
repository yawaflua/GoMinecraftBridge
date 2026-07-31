package dev.yawaflua.gominecraftbridge.catalog;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/** Backend boundary used by the catalog application service. */
public interface CatalogApi {
	List<CatalogProject> search(String query) throws IOException;

	CatalogVersion version(String projectId, String reference) throws IOException;

	List<CatalogUpdate> checkNewVersions(List<InstalledCatalogPackage> installed) throws IOException;

	Download download(String slug, String version) throws IOException;

	URI downloadUri(String slug, String version);

	record Download(URI uri, String contentType, byte[] data) {
		public Download {
			contentType = contentType == null ? "application/octet-stream" : contentType;
			data = data == null ? new byte[0] : data;
		}
	}
}
