package dev.yawaflua.gominecraftbridge.client.ui;

import dev.yawaflua.gominecraftbridge.catalog.CatalogProject;
import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.catalog.InstalledCatalogPackage;
import dev.yawaflua.gominecraftbridge.client.runtime.GbmClientRuntime;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CatalogScreenSection {
	private CatalogScreenSection() {
	}

	public static void add(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			GbmClientRuntime runtime,
			CatalogScreenEdits edits
	) {
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("GBM catalog"));
		category.addEntry(entries.startTextDescription(Component.literal(runtime.catalog().status())).build());
		category.addEntry(entries.startStrField(Component.literal("Backend URL"), edits.backendUrl())
				.setDefaultValue(GbmCatalogService.DEFAULT_BACKEND_URL)
				.setSaveConsumer(edits::backendUrl)
				.setTooltip(Component.literal("HTTP(S) base URL of the GBM project backend."))
				.build());
		category.addEntry(entries.startBooleanToggle(
				Component.literal("Automatically install updates"), edits.automaticUpdates()
		)
				.setDefaultValue(false)
				.setSaveConsumer(edits::automaticUpdates)
				.setTooltip(Component.literal(
						"Checks all catalog-managed packages at startup. Restart Minecraft to activate replacements."
				))
				.build());
		category.addEntry(entries.startStrField(Component.literal("Search published packages"), "")
				.setDefaultValue("")
				.setSaveConsumer(edits::searchQuery)
				.build());
		category.addEntry(entries.startBooleanToggle(Component.literal("Run search on save"), false)
				.setDefaultValue(false)
				.setSaveConsumer(edits::runSearch)
				.setTooltip(Component.literal("Save, then reopen this screen to select a result."))
				.build());

		Map<String, InstalledCatalogPackage> installed = runtime.catalog().installedPackages().stream()
				.collect(Collectors.toMap(
						InstalledCatalogPackage::projectId,
						Function.identity(),
						(left, right) -> right
				));
		for (CatalogProject project : runtime.catalog().searchResults()) {
			addProject(entries, category, edits, project, installed.get(project.id()));
		}
	}

	private static void addProject(
			ConfigEntryBuilder entries,
			ConfigCategory category,
			CatalogScreenEdits edits,
			CatalogProject project,
			InstalledCatalogPackage current
	) {
		String details = project.name() + " [" + project.slug() + "]"
				+ " • latest " + value(project.latestVersion())
				+ (current == null ? "" : " • installed " + current.version())
				+ "\n" + value(project.description());
		category.addEntry(entries.startTextDescription(Component.literal(details)).build());
		category.addEntry(entries.startBooleanToggle(
				Component.literal((current == null ? "Install " : "Update ") + project.name() + " on save"),
				false
		)
				.setDefaultValue(false)
				.setSaveConsumer(enabled -> {
					if (enabled) {
						edits.installs().add(project.id());
					}
				})
				.build());
	}

	private static String value(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
