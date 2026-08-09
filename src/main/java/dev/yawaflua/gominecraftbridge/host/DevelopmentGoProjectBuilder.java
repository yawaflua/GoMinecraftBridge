package dev.yawaflua.gominecraftbridge.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds explicitly allowed Go modules during a Fabric development run. */
public final class DevelopmentGoProjectBuilder {
	private static final int MAX_BUILD_OUTPUT_BYTES = 64 * 1024;

	private final Path settingsFile;
	private final Path outputDirectory;
	private final GoCompiler compiler;
	private final Set<String> builtModules = new LinkedHashSet<>();

	public DevelopmentGoProjectBuilder(Path settingsFile, Path outputDirectory) {
		this(settingsFile, outputDirectory, DevelopmentGoProjectBuilder::compile);
	}

	DevelopmentGoProjectBuilder(Path settingsFile, Path outputDirectory, GoCompiler compiler) {
		this.settingsFile = settingsFile;
		this.outputDirectory = outputDirectory;
		this.compiler = compiler;
	}

	public synchronized BuildResult buildProjects(Path searchRoot) {
		List<Path> packages = new ArrayList<>();
		List<NativePackageScanner.ScanFailure> failures = new ArrayList<>();
		Set<String> allowedModules;
		try {
			allowedModules = allowedModules();
		} catch (IOException exception) {
			return new BuildResult(packages, List.of(new NativePackageScanner.ScanFailure(this.settingsFile, exception)));
		}
		if (allowedModules.isEmpty()) {
			return new BuildResult(packages, failures);
		}

		try (var files = Files.walk(searchRoot)) {
			for (Path goMod : files.filter(path -> path.getFileName().toString().equals("go.mod")).toList()) {
				try {
					String module = modulePath(goMod);
					if (!allowedModules.contains(module)) {
						continue;
					}
					Files.createDirectories(this.outputDirectory);
					Path output = this.outputDirectory.resolve(fileName(module));
					if (this.builtModules.add(module)) {
						try {
							this.compiler.compile(goMod.getParent(), output);
						} catch (IOException exception) {
							this.builtModules.remove(module);
							throw exception;
						}
					}
					packages.add(output);
				} catch (IOException exception) {
					failures.add(new NativePackageScanner.ScanFailure(goMod.getParent(), exception));
				}
			}
		} catch (IOException exception) {
			failures.add(new NativePackageScanner.ScanFailure(searchRoot, exception));
		}
		return new BuildResult(packages, failures);
	}

	private Set<String> allowedModules() throws IOException {
		if (!Files.isRegularFile(this.settingsFile)) {
			return Set.of();
		}
		Set<String> modules = new LinkedHashSet<>();
		for (String line : Files.readAllLines(this.settingsFile)) {
			String value = line.strip();
			if (!value.isEmpty() && !value.startsWith("#")) {
				modules.add(value);
			}
		}
		return modules;
	}

	private static String modulePath(Path goMod) throws IOException {
		for (String line : Files.readAllLines(goMod)) {
			String value = line.strip();
			if (value.startsWith("module ")) {
				String module = value.substring("module ".length()).strip();
				if (!module.isEmpty()) {
					return module;
				}
			}
		}
		throw new IOException("Cannot find module directive in " + goMod);
	}

	private static String fileName(String module) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(module.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12) + NativePackageScanner.nativeExtension();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void compile(Path project, Path output) throws IOException {
		Process process = new ProcessBuilder(
				"go", "build", "-buildmode=c-shared", "-o", output.toAbsolutePath().toString(), "."
		).directory(project.toFile()).redirectErrorStream(true).start();
		byte[] buildOutput = process.getInputStream().readNBytes(MAX_BUILD_OUTPUT_BYTES + 1);
		if (buildOutput.length > MAX_BUILD_OUTPUT_BYTES) {
			process.destroyForcibly();
			throw new IOException("Go build output exceeded " + MAX_BUILD_OUTPUT_BYTES + " bytes for " + project);
		}
		try {
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				String message = new String(buildOutput, StandardCharsets.UTF_8).strip();
				throw new IOException("go build failed for " + project + " with exit code " + exitCode
						+ (message.isEmpty() ? "" : ": " + message));
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Interrupted while building Go project " + project, exception);
		}
	}

	@FunctionalInterface
	interface GoCompiler {
		void compile(Path project, Path output) throws IOException;
	}

	public record BuildResult(List<Path> packages, List<NativePackageScanner.ScanFailure> failures) {
		public BuildResult {
			packages = List.copyOf(packages);
			failures = List.copyOf(failures);
		}
	}
}
