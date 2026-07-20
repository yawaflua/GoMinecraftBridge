package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.PluginEnvironment;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClothManagementScreen {
	private static final int VISIBLE_LOG_LINES = 100;
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private ClothManagementScreen() {
	}

	public static Screen create(Screen parent, boolean refresh) {
		if (refresh) {
			GoMinecraftBridgeClient.requestRefresh();
		}

		GoMinecraftBridgeClient.ConnectionStatus connectionStatus =
				GoMinecraftBridgeClient.connectionStatus();
		BridgeManagementSnapshot snapshot = connectionStatus
				== GoMinecraftBridgeClient.ConnectionStatus.AVAILABLE
				? GoMinecraftBridgeClient.latest()
				: null;
		BridgeManagementSnapshot localSnapshot = GoMinecraftBridgeClient.localPlugins();
		Set<String> serverReloads = new LinkedHashSet<>();
		Set<String> clientReloads = new LinkedHashSet<>();
		boolean[] serverRescan = {false};
		boolean[] clientRescan = {false};
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Go Minecraft Bridge"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory clientPackages = builder.getOrCreateCategory(Component.literal("Client packages"));
		addOverview(clientPackages, entries, localSnapshot, clientRescan, "Client", "client-plugins");
		for (ManagedPluginSnapshot plugin : localSnapshot.plugins()) {
			addPluginCategory(
					builder, entries, plugin, localSnapshot.canReload(), clientReloads,
					"Client", "Reload unavailable: the client native runtime is stopped."
			);
		}

		ConfigCategory serverPackages = builder.getOrCreateCategory(Component.literal("Server packages"));

		if (snapshot == null) {
			addConnectionStatus(serverPackages, entries, connectionStatus);
		} else {
			addOverview(serverPackages, entries, snapshot, serverRescan, "Server", "plugins or mods");
			for (ManagedPluginSnapshot plugin : snapshot.plugins()) {
				addPluginCategory(
						builder, entries, plugin, snapshot.canReload(), serverReloads,
						"Server", "Reload unavailable: server operator permission is required."
				);
			}
		}

		builder.setSavingRunnable(() -> {
			if (clientRescan[0]) {
				GoMinecraftBridgeClient.requestLocalRescan();
			}
			for (String pluginId : clientReloads) {
				GoMinecraftBridgeClient.requestLocalReload(pluginId);
			}
			if (serverRescan[0]) {
				GoMinecraftBridgeClient.requestRescan();
			}
			for (String pluginId : serverReloads) {
				GoMinecraftBridgeClient.requestReload(pluginId);
			}
			if (serverReloads.isEmpty() && !serverRescan[0] && GoMinecraftBridgeClient.channelAvailable()) {
				GoMinecraftBridgeClient.requestRefresh();
			}
		});

		Screen screen = builder.build();
		GoMinecraftBridgeClient.onUpdate(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.screen == screen) {
				client.setScreen(create(parent, false));
			}
		});
		return screen;
	}

	private static void addConnectionStatus(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			GoMinecraftBridgeClient.ConnectionStatus status
	) {
		String message = switch (status) {
			case DISCONNECTED -> "Join a server to inspect its Go packages and plugins.";
			case CONNECTING -> "Connecting to the server and checking Go Minecraft Bridge support…";
			case UNSUPPORTED -> "This server does not have Go Minecraft Bridge. "
					+ "You can keep this client mod installed, but server package and plugin "
					+ "management is unavailable here.";
			case AVAILABLE -> "Requesting package and plugin state from the server…";
		};
		category.addEntry(entries.startTextDescription(Component.literal(message)).build());
	}

	private static void addOverview(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			BridgeManagementSnapshot snapshot,
			boolean[] rescan,
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
					scope.equals("Server")
							? "Package details, plugin logs, reload, and rescan require server operator permission."
							: "Client package reload and rescan are unavailable while the runtime is stopped."
			)).build());
			return;
		}

		for (PackageInspection inspected : snapshot.packages()) {
			String status = inspected.valid() ? "✓ valid" : "✗ invalid";
			String details = inspected.valid()
					? "plugin id: " + inspected.pluginId()
					: inspected.error();
			category.addEntry(entries.startTextDescription(Component.literal(
					status + " — " + inspected.path() + "\n" + details
			)).build());
		}
		if (snapshot.packages().isEmpty()) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"No native packages found in config/go-minecraft-bridge/" + directories + "."
			)).build());
		}
		category.addEntry(entries.startBooleanToggle(
					Component.literal("Rescan package folders on save"), false
			)
				.setDefaultValue(false)
				.setSaveConsumer(enabled -> rescan[0] = enabled)
				.setTooltip(Component.literal(
						"Rescan " + directories + ". Already loaded native packages are not unloaded."
				))
				.build());
	}

	private static void addPluginCategory(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ManagedPluginSnapshot plugin,
			boolean canReload,
			Set<String> reloads,
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
		category.addEntry(entries.startTextDescription(Component.literal(
				(metadata.description() == null ? "" : metadata.description())
		)).build());
		category.addEntry(entries.startTextDescription(Component.literal(
				"ABI/API: " + metadata.apiVersion()
						+ "\nEnvironment: " + environment(metadata.environment())
						+ "\nAuthors: " + String.join(", ", metadata.authors())
						+ "\nSite: " + value(metadata.website())
						+ "\nBackend: " + plugin.backend()
						+ "\nPath: " + plugin.origin()
		)).build());

		if (metadata.configSchema() != null) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Config schema:\n" + ProtocolJson.GSON.toJson(metadata.configSchema())
			)).build());
		}

		if (canReload) {
			category.addEntry(entries.startBooleanToggle(
						Component.literal("Reinitialize lifecycle on save"), false
				)
					.setDefaultValue(false)
					.setSaveConsumer(enabled -> {
						if (enabled) {
							reloads.add(metadata.id());
						}
					})
					.setTooltip(Component.literal(
							"Calls Deinit → Init. A changed native binary is loaded only after a JVM restart."
					))
					.build());
		}

		List<PluginLog> logs = plugin.logs();
		int from = Math.max(0, logs.size() - VISIBLE_LOG_LINES);
		category.addEntry(entries.startTextDescription(Component.literal(
				"Last logs (" + (logs.size() - from) + " from " + logs.size() + "):"
		)).build());
		for (PluginLog log : logs.subList(from, logs.size())) {
			category.addEntry(entries.startTextDescription(Component.literal(format(log))).build());
		}
		if (!canReload) {
			category.addEntry(entries.startTextDescription(Component.literal(
					unavailableMessage
			)).build());
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
