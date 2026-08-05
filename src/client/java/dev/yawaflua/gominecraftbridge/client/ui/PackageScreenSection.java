package dev.yawaflua.gominecraftbridge.client.ui;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PackageScreenSection {
	private static final int VISIBLE_LOG_LINES = 100;
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private PackageScreenSection() {
	}

	public static void addOverview(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			BridgeManagementSnapshot snapshot,
			PackageScreenActions actions,
			String scope,
			String directories
	) {
		category.addEntry(entries.startTextDescription(Component.literal(
				scope + " runtime: " + (snapshot.serverRunning() ? "running" : "stopped")
						+ " • packages: " + snapshot.packages().size()
						+ " • plugins: " + snapshot.plugins().size()
		)).build());
		if (snapshot.message() != null && !snapshot.message().isBlank()) {
			category.addEntry(entries.startTextDescription(Component.literal(snapshot.message())).build());
		}
		if (!snapshot.canReload()) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Client reload and rescan are unavailable while the native runtime is stopped."
			)).build());
			return;
		}

		for (PackageInspection inspected : snapshot.packages()) {
			String status = inspected.valid() ? "✓ valid" : "✗ invalid";
			String details = inspected.valid() ? "plugin id: " + inspected.pluginId() : inspected.error();
			category.addEntry(entries.startTextDescription(Component.literal(
					status + " — " + inspected.path() + "\n" + details
			)).build());
		}
		if (snapshot.packages().isEmpty()) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"No native packages found in " + directories + "."
			)).build());
		}
		category.addEntry(entries.startBooleanToggle(Component.literal("Rescan package folders on save"), false)
				.setDefaultValue(false)
				.setSaveConsumer(actions::rescan)
				.setTooltip(Component.literal("Already loaded native packages are not unloaded."))
				.build());
	}

	public static void addPlugin(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ManagedPluginSnapshot plugin,
			boolean canReload,
			PackageScreenActions actions,
			String scope,
			String unavailableMessage
	) {
		PluginMetadata metadata = plugin.metadata();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal(
				scope + " — " + metadata.name() + " [" + metadata.id() + "]"
		));
		category.addEntry(entries.startTextDescription(Component.literal(
				metadata.id() + " " + metadata.version() + " • " + plugin.state()
		)).build());
		category.addEntry(entries.startTextDescription(Component.literal(value(metadata.description()))).build());
		category.addEntry(entries.startTextDescription(Component.literal(
				"ABI/API: " + metadata.apiVersion()
						+ "\nEnvironment: " + environment(metadata.environment())
						+ "\nAuthors: " + String.join(", ", metadata.authors())
						+ "\nSite: " + value(metadata.website())
						+ "\nBackend: " + value(plugin.backend())
						+ "\nPath: " + value(plugin.origin())
		)).build());

		if (metadata.configSchema() != null) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Config schema:\n" + ProtocolJson.GSON.toJson(metadata.configSchema())
			)).build());
		}
		if (canReload) {
			category.addEntry(entries.startBooleanToggle(Component.literal("Reinitialize lifecycle on save"), false)
					.setDefaultValue(false)
					.setSaveConsumer(enabled -> {
						if (enabled) {
							actions.reloads().add(metadata.id());
						}
					})
					.setTooltip(Component.literal(
							"Calls Deinit → Init. A changed native binary is loaded after a JVM restart."
					))
					.build());
		}
		addLogs(entries, category, plugin.logs());
		if (!canReload) {
			category.addEntry(entries.startTextDescription(Component.literal(unavailableMessage)).build());
		}
	}

	private static void addLogs(
			ConfigEntryBuilder entries,
			ConfigCategory category,
			List<PluginLog> logs
	) {
		int from = Math.max(0, logs.size() - VISIBLE_LOG_LINES);
		category.addEntry(entries.startTextDescription(Component.literal(
				"Last logs (" + (logs.size() - from) + " from " + logs.size() + "):"
		)).build());
		for (PluginLog log : logs.subList(from, logs.size())) {
			category.addEntry(entries.startTextDescription(Component.literal(format(log))).build());
		}
	}

	private static String environment(PluginEnvironment environment) {
		return switch (environment) {
			case SERVER -> "server";
			case CLIENT -> "client";
			case BOTH -> "client + server";
		};
	}

	private static String format(PluginLog log) {
		return "[" + LOG_TIME.format(Instant.ofEpochMilli(log.timestampUnixMilli())) + "]"
				+ " [" + value(log.level()) + "/" + value(log.stream()) + "] " + value(log.message());
	}

	private static String value(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
