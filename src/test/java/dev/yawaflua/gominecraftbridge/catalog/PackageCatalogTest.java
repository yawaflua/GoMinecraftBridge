package dev.yawaflua.gominecraftbridge.catalog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbmCatalogServiceTest {
	@TempDir
	Path temporaryDirectory;

	private HttpServer server;
	private byte[] library;
	private String reportedHash;

	@BeforeEach
	void startServer() throws IOException {
		this.library = new byte[]{0x7f, 'E', 'L', 'F', 1, 2, 3};
		this.reportedHash = sha256(this.library);
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/v1/projects/project-id/versions", exchange -> json(exchange, """
				{"versions":[{"id":"version-id","project_id":"project-id","version":"1.2.0",
				"size_bytes":%d,"sha256":"%s","tag":"VERSION_TAG_RELEASE","metadata":{
				"slug":"weather","environment":"PLUGIN_ENVIRONMENT_CLIENT"}}]}
				""".formatted(this.library.length, this.reportedHash)));
		this.server.createContext("/v1/projects/slug/weather/versions/1.2.0:download", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, this.library.length);
			exchange.getResponseBody().write(this.library);
			exchange.close();
		});
		this.server.start();
	}

	@Test
	void rejectsDownloadWhoseDigestDoesNotMatchVersionMetadata() throws IOException {
		GbmCatalogService catalog = new GbmCatalogService(this.temporaryDirectory.resolve("gbm"));
		catalog.saveSettings("http://127.0.0.1:" + this.server.getAddress().getPort(), false);
		this.library[this.library.length - 1] = 99;

		assertThrows(IOException.class, () -> catalog.install(new CatalogProject(
				"project-id", "weather", "Weather", "", "1.2.0", 1
		)));
		assertTrue(catalog.installedPackages().isEmpty());
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void installsVerifiedPackageAndPersistsSourceManifest() throws IOException {
		GbmCatalogService catalog = new GbmCatalogService(this.temporaryDirectory.resolve("gbm"));
		catalog.saveSettings("http://127.0.0.1:" + this.server.getAddress().getPort(), true);

		InstalledCatalogPackage installed = catalog.install(new CatalogProject(
				"project-id", "weather", "Weather", "", "1.2.0", 1
		));

		Path packageDirectory = this.temporaryDirectory.resolve("gbm/client-plugins/weather");
		assertEquals("1.2.0", installed.version());
		assertTrue(installed.downloadUrl().endsWith("/v1/projects/slug/weather/versions/1.2.0:download"));
		assertTrue(Files.isRegularFile(packageDirectory.resolve("weather" + PackageInstaller.nativeExtension())));
		assertTrue(Files.readString(packageDirectory.resolve(GbmCatalogService.SIDECAR_FILE)).contains("downloadUrl"));
		assertTrue(Files.readString(this.temporaryDirectory.resolve("gbm/repository.json")).contains("project-id"));

		GbmCatalogService reloaded = new GbmCatalogService(this.temporaryDirectory.resolve("gbm"));
		assertTrue(reloaded.settings().automaticUpdates());
		assertEquals(1, reloaded.installedPackages().size());
	}

	private static void json(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}
}
