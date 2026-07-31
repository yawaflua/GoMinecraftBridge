package dev.yawaflua.gominecraftbridge.host;

import dev.yawaflua.gominecraftbridge.backend.nativeffi.NativePluginBackend;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Shared native package discovery and loaded-plugin registry. */
public final class NativePluginRegistry {
	private final NativePackageScanner scanner;
	private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();
	private List<PackageInspection> inspections = List.of();

	public NativePluginRegistry(NativePackageScanner scanner) {
		this.scanner = scanner;
	}

	public synchronized DiscoveryReport discover(Predicate<PluginMetadata> accepts) {
		NativePackageScanner.ScanResult scan = this.scanner.scan();
		List<PackageInspection> currentInspections = new ArrayList<>();
		List<LoadedPlugin> discovered = new ArrayList<>();
		List<SkippedPlugin> skipped = new ArrayList<>();
		List<PackageFailure> failures = new ArrayList<>();

		for (Path candidate : scan.packages()) {
			Path normalized = candidate.toAbsolutePath().normalize();
			try {
				LoadedPlugin existing = pluginByOrigin(normalized);
				if (existing != null) {
					currentInspections.add(validInspection(normalized, existing));
					continue;
				}
				LoadedPlugin plugin = new LoadedPlugin(new NativePluginBackend(candidate));
				if (!accepts.test(plugin.metadata())) {
					currentInspections.add(validInspection(normalized, plugin));
					skipped.add(new SkippedPlugin(normalized, plugin));
					continue;
				}
				if (this.plugins.putIfAbsent(plugin.metadata().id(), plugin) != null) {
					throw new IllegalArgumentException("Duplicate plugin id " + plugin.metadata().id());
				}
				currentInspections.add(validInspection(normalized, plugin));
				discovered.add(plugin);
			} catch (RuntimeException exception) {
				currentInspections.add(new PackageInspection(
						normalized.toString(), false, null, rootMessage(exception)
				));
				failures.add(new PackageFailure(normalized, exception));
			}
		}
		this.inspections = List.copyOf(currentInspections);
		return new DiscoveryReport(discovered, skipped, failures, scan.failures());
	}

	public synchronized LoadedPlugin plugin(String pluginId) {
		return this.plugins.get(pluginId);
	}

	public synchronized List<LoadedPlugin> plugins() {
		return List.copyOf(this.plugins.values());
	}

	public synchronized List<LoadedPlugin> runningPlugins() {
		return this.plugins.values().stream()
				.filter(plugin -> plugin.state() == PluginState.RUNNING)
				.toList();
	}

	public synchronized List<PackageInspection> inspections() {
		return this.inspections;
	}

	private LoadedPlugin pluginByOrigin(Path origin) {
		return this.plugins.values().stream()
				.filter(plugin -> plugin.backend().origin().equals(origin))
				.findFirst()
				.orElse(null);
	}

	private static PackageInspection validInspection(Path path, LoadedPlugin plugin) {
		return new PackageInspection(path.toString(), true, plugin.metadata().id(), null);
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	public record SkippedPlugin(Path path, LoadedPlugin plugin) {
	}

	public record PackageFailure(Path path, RuntimeException cause) {
	}

	public record DiscoveryReport(
			List<LoadedPlugin> discovered,
			List<SkippedPlugin> skipped,
			List<PackageFailure> failures,
			List<NativePackageScanner.ScanFailure> scanFailures
	) {
		public DiscoveryReport {
			discovered = List.copyOf(discovered);
			skipped = List.copyOf(skipped);
			failures = List.copyOf(failures);
			scanFailures = List.copyOf(scanFailures);
		}
	}
}
