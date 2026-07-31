package dev.yawaflua.gominecraftbridge.catalog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendCatalogClientTest {
	private HttpServer server;
	private BackendCatalogClient client;

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.start();
		this.client = new BackendCatalogClient("http://127.0.0.1:" + this.server.getAddress().getPort());
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void searchesUsingProtoJsonFieldNames() throws IOException {
		this.server.createContext("/v1/projects:search", exchange -> json(exchange, """
				{"projects":[{"project":{"id":"project-id","slug":"weather","name":"Weather",
				"description":"Forecast HUD","latest_version":"1.2.0"},"similarity":0.91}]}
				"""));

		List<CatalogProject> projects = this.client.search("weather hud");

		assertEquals(1, projects.size());
		assertEquals("weather", projects.getFirst().slug());
		assertEquals("1.2.0", projects.getFirst().latestVersion());
	}

	@Test
	void sendsInstalledPackagesAsPostBody() throws IOException {
		AtomicReference<String> method = new AtomicReference<>();
		AtomicReference<String> body = new AtomicReference<>();
		this.server.createContext("/v1/projects/@all/versions/check", exchange -> {
			method.set(exchange.getRequestMethod());
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			json(exchange, """
					{"updates":[{"project_id":"project-id","current_version":"1.0.0",
					"latest_version":"1.1.0","update_available":true}]}
					""");
		});
		InstalledCatalogPackage installed = new InstalledCatalogPackage(
				"project-id", "weather", "weather", "1.0.0", "", "", "", 0
		);

		List<CatalogUpdate> updates = this.client.checkNewVersions(List.of(installed));

		assertEquals("POST", method.get());
		assertTrue(body.get().contains("\"project_id\":\"project-id\""));
		assertEquals("1.1.0", updates.getFirst().latestVersion());
		assertTrue(updates.getFirst().updateAvailable());
	}

	private static void json(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
}
