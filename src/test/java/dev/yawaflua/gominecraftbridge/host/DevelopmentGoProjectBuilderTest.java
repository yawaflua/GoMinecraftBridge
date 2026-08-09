package dev.yawaflua.gominecraftbridge.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DevelopmentGoProjectBuilderTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void buildsOnlyExplicitlyAllowedModules() throws Exception {
		Path projects = Files.createDirectories(this.temporaryDirectory.resolve("plugins"));
		Path allowed = createProject(projects.resolve("allowed"), "example.com/allowed");
		createProject(projects.resolve("denied"), "example.com/denied");
		Path settings = this.temporaryDirectory.resolve("development-modules.txt");
		Files.writeString(settings, "# enabled during development\nexample.com/allowed\n");
		List<Path> compiled = new ArrayList<>();
		DevelopmentGoProjectBuilder builder = new DevelopmentGoProjectBuilder(
				settings, this.temporaryDirectory.resolve("builds"),
				(project, output) -> {
					compiled.add(project);
					Files.writeString(output, "native");
				}
		);

		DevelopmentGoProjectBuilder.BuildResult result = builder.buildProjects(projects);
		DevelopmentGoProjectBuilder.BuildResult rescanned = builder.buildProjects(projects);

		assertEquals(List.of(allowed), compiled);
		assertEquals(1, result.packages().size());
		assertEquals(result.packages(), rescanned.packages());
		assertTrue(Files.isRegularFile(result.packages().getFirst()));
		assertTrue(result.failures().isEmpty());
	}

	@Test
	void missingSettingsDisablesProjectBuilds() throws Exception {
		Path projects = Files.createDirectories(this.temporaryDirectory.resolve("plugins"));
		createProject(projects.resolve("project"), "example.com/project");
		List<Path> compiled = new ArrayList<>();
		DevelopmentGoProjectBuilder builder = new DevelopmentGoProjectBuilder(
				this.temporaryDirectory.resolve("missing.txt"), this.temporaryDirectory.resolve("builds"),
				(project, output) -> compiled.add(project)
		);

		DevelopmentGoProjectBuilder.BuildResult result = builder.buildProjects(projects);

		assertTrue(compiled.isEmpty());
		assertTrue(result.packages().isEmpty());
		assertTrue(result.failures().isEmpty());
	}

	private static Path createProject(Path directory, String module) throws Exception {
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("go.mod"), "module " + module + "\n\ngo 1.25\n");
		return directory;
	}
}
