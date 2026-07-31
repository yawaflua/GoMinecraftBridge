package dev.yawaflua.gominecraftbridge.catalog;

import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes source metadata next to each GBM-managed native package. */
final class PackageManifestStore {
	static final String FILE_NAME = "gbm-package.json";

	private final Path packagesRoot;

	PackageManifestStore(Path packagesRoot) {
		this.packagesRoot = packagesRoot.toAbsolutePath().normalize();
	}

	void save(InstalledCatalogPackage installed) throws IOException {
		Path directory = this.packagesRoot.resolve(installed.slug()).normalize();
		if (!directory.startsWith(this.packagesRoot) || !Files.isDirectory(directory)) {
			throw new IOException("Installed GBM package directory is missing");
		}
		Path temporary = directory.resolve(FILE_NAME + ".tmp");
		Files.writeString(temporary, ProtocolJson.GSON.toJson(installed));
		AtomicFiles.replace(temporary, directory.resolve(FILE_NAME));
	}
}
