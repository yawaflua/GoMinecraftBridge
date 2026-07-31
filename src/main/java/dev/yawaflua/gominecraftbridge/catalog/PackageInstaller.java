package dev.yawaflua.gominecraftbridge.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs raw native libraries and ZIP packages into one directory per project. */
public final class PackageInstaller {
	private static final int MAX_ARCHIVE_ENTRIES = 4096;
	private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;

	private PackageInstaller() {
	}

	public static Path install(Path packagesRoot, String slug, byte[] archive, String contentType)
			throws IOException {
		validateSlug(slug);
		if (archive == null || archive.length == 0) {
			throw new IOException("Downloaded package is empty");
		}
		Files.createDirectories(packagesRoot);
		Path normalizedRoot = packagesRoot.toAbsolutePath().normalize();
		Path staging = normalizedRoot.resolve(".staging-" + slug + "-" + UUID.randomUUID());
		Path target = normalizedRoot.resolve(slug);
		Path backup = normalizedRoot.resolve(".backup-" + slug + "-" + UUID.randomUUID());
		Files.createDirectories(staging);

		boolean targetMoved = false;
		try {
			if (isZip(archive, contentType)) {
				extractZip(staging, archive);
			} else {
				Files.write(staging.resolve(slug + nativeExtension()), archive);
			}
			Path binary = arrangeNativeBinary(staging, slug);

			if (Files.exists(target)) {
				move(target, backup);
				targetMoved = true;
			}
			try {
				move(staging, target);
			} catch (IOException exception) {
				if (targetMoved && !Files.exists(target)) {
					move(backup, target);
					targetMoved = false;
				}
				throw exception;
			}
			if (targetMoved) {
				deleteTree(backup);
			}
			return target.resolve(staging.relativize(binary));
		} finally {
			deleteTree(staging);
			if (!targetMoved || Files.exists(target)) {
				deleteTree(backup);
			}
		}
	}

	private static void extractZip(Path staging, byte[] archive) throws IOException {
		int entries = 0;
		long extracted = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				entries++;
				if (entries > MAX_ARCHIVE_ENTRIES) {
					throw new IOException("ZIP package contains too many entries");
				}
				Path destination = staging.resolve(entry.getName()).normalize();
				if (!destination.startsWith(staging)) {
					throw new IOException("ZIP entry escapes the package directory: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(destination);
					continue;
				}
				Files.createDirectories(destination.getParent());
				extracted += copyLimited(zip, destination, MAX_EXTRACTED_BYTES - extracted);
				if (extracted > MAX_EXTRACTED_BYTES) {
					throw new IOException("ZIP package expands beyond 256 MiB");
				}
			}
		}
	}

	private static long copyLimited(InputStream input, Path destination, long remaining) throws IOException {
		if (remaining < 0) {
			throw new IOException("ZIP package expands beyond 256 MiB");
		}
		long written = 0;
		try (var output = Files.newOutputStream(destination)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;) {
				written += read;
				if (written > remaining) {
					throw new IOException("ZIP package expands beyond 256 MiB");
				}
				output.write(buffer, 0, read);
			}
		}
		return written;
	}

	private static Path arrangeNativeBinary(Path staging, String slug) throws IOException {
		String extension = nativeExtension();
		Path expected = staging.resolve(slug + extension);
		if (Files.isRegularFile(expected)) {
			return expected;
		}
		List<Path> candidates = new ArrayList<>();
		try (var paths = Files.walk(staging)) {
			paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
					.sorted(Comparator.comparing(Path::toString))
					.forEach(candidates::add);
		}
		if (candidates.isEmpty()) {
			throw new IOException("Package does not contain a " + extension + " library for this operating system");
		}
		Path preferred = candidates.stream()
				.filter(path -> path.getFileName().toString().equalsIgnoreCase(slug + extension))
				.findFirst()
				.orElseGet(() -> candidates.size() == 1 ? candidates.getFirst() : null);
		if (preferred == null) {
			throw new IOException("Package contains multiple native libraries and none is named " + slug + extension);
		}
		Files.move(preferred, expected, StandardCopyOption.REPLACE_EXISTING);
		return expected;
	}

	private static boolean isZip(byte[] archive, String contentType) {
		boolean signature = archive.length >= 4
				&& archive[0] == 'P' && archive[1] == 'K'
				&& ((archive[2] == 3 && archive[3] == 4)
				|| (archive[2] == 5 && archive[3] == 6)
				|| (archive[2] == 7 && archive[3] == 8));
		return signature || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("zip"));
	}

	private static void move(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	public static String nativeExtension() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return ".dll";
		}
		if (os.contains("mac")) {
			return ".dylib";
		}
		return ".so";
	}

	private static void validateSlug(String slug) {
		if (slug == null || !slug.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
			throw new IllegalArgumentException("Invalid project slug: " + slug);
		}
	}
}
