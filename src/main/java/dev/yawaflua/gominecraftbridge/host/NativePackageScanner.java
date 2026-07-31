package dev.yawaflua.gominecraftbridge.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Platform-neutral discovery of native GBM package files. */
public final class NativePackageScanner {
	private final List<SearchRoot> roots;

	public NativePackageScanner(List<SearchRoot> roots) {
		this.roots = List.copyOf(roots);
	}

	public ScanResult scan() {
		String extension = nativeExtension();
		List<Path> packages = new ArrayList<>();
		List<ScanFailure> failures = new ArrayList<>();
		for (SearchRoot root : this.roots) {
			if (!Files.isDirectory(root.path())) {
				continue;
			}
			int depth = root.recursive() ? Integer.MAX_VALUE : 1;
			try (var files = Files.walk(root.path(), depth)) {
				files.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
						.forEach(packages::add);
			} catch (IOException exception) {
				failures.add(new ScanFailure(root.path(), exception));
			}
		}
		packages.sort(Comparator.comparing(Path::toString));
		return new ScanResult(packages, failures);
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

	public record SearchRoot(Path path, boolean recursive) {
	}

	public record ScanFailure(Path root, IOException cause) {
	}

	public record ScanResult(List<Path> packages, List<ScanFailure> failures) {
		public ScanResult {
			packages = List.copyOf(packages);
			failures = List.copyOf(failures);
		}
	}
}
