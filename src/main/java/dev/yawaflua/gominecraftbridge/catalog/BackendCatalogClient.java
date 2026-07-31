package dev.yawaflua.gominecraftbridge.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Small HTTP/JSON client for the public project catalog endpoints. */
public final class BackendCatalogClient implements CatalogApi {
	public static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
	private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private final URI baseUri;
	private final HttpClient http;
	private final Gson gson;

	public BackendCatalogClient(String baseUrl) {
		this(baseUrl, HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build(), ProtocolJson.GSON);
	}

	BackendCatalogClient(String baseUrl, HttpClient http, Gson gson) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("Backend URL is required");
		}
		URI parsed = URI.create(baseUrl.trim());
		if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
				|| parsed.getHost() == null) {
			throw new IllegalArgumentException("Backend URL must use http or https");
		}
		String normalized = parsed.toString();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		this.baseUri = URI.create(normalized);
		this.http = http;
		this.gson = gson;
	}

	@Override
	public List<CatalogProject> search(String query) throws IOException {
		String value = query == null ? "" : query.trim();
		if (value.length() > 200) {
			throw new IllegalArgumentException("Search query exceeds 200 characters");
		}
		String path = "/v1/projects:search?query=" + encode(value)
				+ "&pageSize=24&minSimilarity=" + (value.isEmpty() ? "0" : "0.08");
		SearchResponse response = getJson(path, SearchResponse.class);
		if (response == null || response.projects == null) {
			return List.of();
		}
		List<CatalogProject> projects = new ArrayList<>();
		for (SearchResultDto result : response.projects) {
			if (result == null || result.project == null) {
				continue;
			}
			ProjectDto project = result.project;
			projects.add(new CatalogProject(
					project.id, project.slug, project.name, project.description,
					project.latestVersion, result.similarity
			));
		}
		return List.copyOf(projects);
	}

	public List<CatalogVersion> versions(String projectId) throws IOException {
		requireIdentifier(projectId, "project id");
		VersionsResponse response = getJson(
				"/v1/projects/" + encode(projectId) + "/versions?pageSize=100",
				VersionsResponse.class
		);
		if (response == null || response.versions == null) {
			return List.of();
		}
		List<CatalogVersion> versions = new ArrayList<>();
		for (VersionDto version : response.versions) {
			if (version != null) {
				versions.add(version.toCatalogVersion());
			}
		}
		return List.copyOf(versions);
	}

	@Override
	public CatalogVersion version(String projectId, String reference) throws IOException {
		List<CatalogVersion> versions = versions(projectId);
		if (versions.isEmpty()) {
			throw new IOException("Project has no downloadable versions");
		}
		if (reference == null || reference.isBlank() || reference.equals("latest")) {
			return versions.getFirst();
		}
		return versions.stream()
				.filter(version -> version.version().equals(reference) || version.id().equals(reference))
				.findFirst()
				.orElseThrow(() -> new IOException("Version " + reference + " is not available"));
	}

	@Override
	public List<CatalogUpdate> checkNewVersions(List<InstalledCatalogPackage> installed) throws IOException {
		if (installed == null || installed.isEmpty()) {
			return List.of();
		}
		JsonObject body = new JsonObject();
		var packages = new com.google.gson.JsonArray();
		for (InstalledCatalogPackage item : installed) {
			if (item == null || item.projectId().isBlank()) {
				continue;
			}
			JsonObject value = new JsonObject();
			value.addProperty("project_id", item.projectId());
			value.addProperty("version", item.version());
			packages.add(value);
		}
		body.add("packages", packages);
		UpdatesResponse response = postJson(
				"/v1/projects/@all/versions/check", body, UpdatesResponse.class
		);
		if (response == null || response.updates == null) {
			return List.of();
		}
		List<CatalogUpdate> updates = new ArrayList<>();
		for (UpdateDto update : response.updates) {
			if (update != null) {
				updates.add(new CatalogUpdate(
						update.projectId, update.currentVersion,
						update.latestVersion, update.updateAvailable
				));
			}
		}
		return List.copyOf(updates);
	}

	@Override
	public Download download(String slug, String version) throws IOException {
		requireIdentifier(slug, "project slug");
		requireIdentifier(version, "version");
		URI uri = downloadUri(slug, version);
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/zip, application/octet-stream")
				.GET()
				.build();
		HttpResponse<InputStream> response = send(request);
		try (InputStream input = response.body()) {
			ensureSuccess(response.statusCode(), input);
			long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
			if (declaredLength > MAX_ARCHIVE_BYTES) {
				throw new IOException("Package exceeds the 64 MiB download limit");
			}
			byte[] data = readLimited(input, MAX_ARCHIVE_BYTES);
			String contentType = response.headers().firstValue("Content-Type")
					.orElse("application/octet-stream").split(";", 2)[0].trim();
			return new CatalogApi.Download(uri, contentType, data);
		}
	}

	@Override
	public URI downloadUri(String slug, String version) {
		return endpoint("/v1/projects/slug/" + encode(slug) + "/versions/" + encode(version) + ":download");
	}

	private <T> T getJson(String path, Class<T> type) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(endpoint(path))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build();
		return json(request, type);
	}

	private <T> T postJson(String path, Object body, Class<T> type) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(endpoint(path))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(body)))
				.build();
		return json(request, type);
	}

	private <T> T json(HttpRequest request, Class<T> type) throws IOException {
		HttpResponse<InputStream> response = send(request);
		try (InputStream input = response.body()) {
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw httpError(response.statusCode(), input);
			}
			byte[] data = readLimited(input, MAX_JSON_BYTES);
			try {
				return this.gson.fromJson(new String(data, StandardCharsets.UTF_8), type);
			} catch (JsonParseException exception) {
				throw new IOException("Backend returned malformed JSON", exception);
			}
		}
	}

	private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
		try {
			return this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Backend request was interrupted", exception);
		}
	}

	private static void ensureSuccess(int status, InputStream body) throws IOException {
		if (status < 200 || status >= 300) {
			throw httpError(status, body);
		}
	}

	private static IOException httpError(int status, InputStream input) throws IOException {
		String details = new String(readLimited(input, 16 * 1024), StandardCharsets.UTF_8).trim();
		return new IOException("Backend returned HTTP " + status
				+ (details.isEmpty() ? "" : ": " + details));
	}

	private static byte[] readLimited(InputStream input, int maximum) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
		byte[] buffer = new byte[8192];
		int total = 0;
		for (int read; (read = input.read(buffer)) >= 0;) {
			total += read;
			if (total > maximum) {
				throw new IOException("Backend response exceeds " + maximum + " bytes");
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private URI endpoint(String path) {
		return URI.create(this.baseUri + path);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static void requireIdentifier(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}

	private static final class SearchResponse {
		List<SearchResultDto> projects;
	}

	private static final class SearchResultDto {
		ProjectDto project;
		float similarity;
	}

	private static final class ProjectDto {
		String id;
		String slug;
		String name;
		String description;
		@SerializedName(value = "latest_version", alternate = "latestVersion")
		String latestVersion;
	}

	private static final class VersionsResponse {
		List<VersionDto> versions;
	}

	private static final class VersionDto {
		String id;
		@SerializedName(value = "project_id", alternate = "projectId")
		String projectId;
		String version;
		String sha256;
		@SerializedName(value = "size_bytes", alternate = "sizeBytes")
		long sizeBytes;
		String tag;
		MetadataDto metadata;

		CatalogVersion toCatalogVersion() {
			CatalogVersion.Metadata mapped = metadata == null
					? null
					: new CatalogVersion.Metadata(
							metadata.slug, metadata.description, metadata.licenses, metadata.authors,
							metadata.abiVersion, metadata.apiVersion, metadata.environment
					);
			return new CatalogVersion(id, projectId, version, sha256, sizeBytes, tag, mapped);
		}
	}

	private static final class MetadataDto {
		String slug;
		String description;
		List<String> licenses;
		List<String> authors;
		@SerializedName(value = "abi_version", alternate = "abiVersion")
		String abiVersion;
		@SerializedName(value = "api_version", alternate = "apiVersion")
		String apiVersion;
		String environment;
	}

	private static final class UpdatesResponse {
		List<UpdateDto> updates;
	}

	private static final class UpdateDto {
		@SerializedName(value = "project_id", alternate = "projectId")
		String projectId;
		@SerializedName(value = "current_version", alternate = "currentVersion")
		String currentVersion;
		@SerializedName(value = "latest_version", alternate = "latestVersion")
		String latestVersion;
		@SerializedName(value = "update_available", alternate = "updateAvailable")
		boolean updateAvailable;
	}
}
