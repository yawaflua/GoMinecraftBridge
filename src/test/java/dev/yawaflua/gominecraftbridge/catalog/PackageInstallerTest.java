package dev.yawaflua.gominecraftbridge.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageInstallerTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void installsAndAtomicallyReplacesRawNativePackage() throws IOException {
		Path root = this.temporaryDirectory.resolve("client-plugins");
		Path first = PackageInstaller.install(root, "weather", new byte[]{1, 2, 3}, "application/octet-stream");
		Files.writeString(root.resolve("weather").resolve("obsolete.txt"), "old");

		Path second = PackageInstaller.install(root, "weather", new byte[]{4, 5}, "application/octet-stream");

		assertTrue(first.endsWith("weather" + PackageInstaller.nativeExtension()));
		assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(second));
		assertFalse(Files.exists(root.resolve("weather").resolve("obsolete.txt")));
	}

	@Test
	void extractsZipAndPlacesNestedLibraryAtStableSlugPath() throws IOException {
		Path root = this.temporaryDirectory.resolve("client-plugins");
		byte[] archive = zip(Map.of(
				"release/libnative" + PackageInstaller.nativeExtension(), new byte[]{7, 8, 9},
				"release/defaults/config.json", "{}".getBytes()
		));

		Path binary = PackageInstaller.install(root, "weather", archive, "application/zip");

		assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(binary));
		assertTrue(binary.equals(root.toAbsolutePath().resolve("weather/weather" + PackageInstaller.nativeExtension())));
		assertTrue(Files.isRegularFile(root.resolve("weather/release/defaults/config.json")));
	}

	@Test
	void rejectsZipSlipEntries() throws IOException {
		Path root = this.temporaryDirectory.resolve("client-plugins");
		byte[] archive = zip(Map.of(
				"../escaped" + PackageInstaller.nativeExtension(), new byte[]{1}
		));

		assertThrows(IOException.class, () ->
				PackageInstaller.install(root, "weather", archive, "application/zip"));
		assertFalse(Files.exists(this.temporaryDirectory.resolve("escaped" + PackageInstaller.nativeExtension())));
	}

	private static byte[] zip(Map<String, byte[]> files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (var file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue());
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}
}
