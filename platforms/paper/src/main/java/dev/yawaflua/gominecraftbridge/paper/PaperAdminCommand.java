package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.catalog.CatalogProject;
import dev.yawaflua.gominecraftbridge.catalog.CatalogVersion;
import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.catalog.InstalledCatalogPackage;
import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

final class PaperAdminCommand implements CommandExecutor, TabCompleter {
	private static final String PREFIX = ChatColor.DARK_AQUA + "[GBM] " + ChatColor.RESET;
	private static final long CONFIRMATION_TTL_MILLIS = 60_000;
	private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

	private final JavaPlugin paperPlugin;
	private final PaperGoPluginManager plugins;
	private final GbmCatalogService catalog;
	private final Map<String, PendingInstall> pendingInstalls = new HashMap<>();
	private final Set<String> busySenders = new HashSet<>();
	private final Map<String, Set<CommandSender>> logSubscribers = new HashMap<>();

	PaperAdminCommand(JavaPlugin paperPlugin, PaperGoPluginManager plugins, GbmCatalogService catalog) {
		this.paperPlugin = paperPlugin;
		this.plugins = plugins;
		this.catalog = catalog;
		this.plugins.setLogListener(this::publishLog);
	}

	@Override
	public boolean onCommand(
			CommandSender sender,
			Command command,
			String label,
			String[] args
	) {
		if (sender instanceof Player player && !player.isOp()) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Server operator permission is required.");
			return true;
		}

		String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
		switch (action) {
			case "status" -> status(sender, this.plugins.managementSnapshot(null));
			case "packages" -> packages(sender, this.plugins.managementSnapshot(null));
			case "metadata" -> metadata(sender, this.plugins.managementSnapshot(null), args);
			case "logs" -> logs(sender, this.plugins.managementSnapshot(null), args);
			case "subscribe" -> subscribe(sender, args);
			case "unsubscribe" -> unsubscribe(sender, args);
			case "reload" -> reload(sender, args);
			case "rescan" -> result(sender, this.plugins.rescan());
			case "load" -> load(sender, args);
			default -> usage(sender, label);
		}
		return true;
	}

	private static void status(CommandSender sender, BridgeManagementSnapshot snapshot) {
		sender.sendMessage(PREFIX + "runtime=" + (snapshot.serverRunning() ? "running" : "stopped")
				+ ", packages=" + snapshot.packages().size() + ", plugins=" + snapshot.plugins().size());
		for (ManagedPluginSnapshot plugin : snapshot.plugins()) {
			sender.sendMessage(" - " + plugin.metadata().id() + " " + plugin.metadata().version()
					+ " [" + plugin.state() + ", "
					+ plugin.metadata().environment().name().toLowerCase(Locale.ROOT) + "]");
		}
	}

	private static void packages(CommandSender sender, BridgeManagementSnapshot snapshot) {
		if (snapshot.packages().isEmpty()) {
			sender.sendMessage(PREFIX + "No native packages found.");
			return;
		}
		for (PackageInspection inspected : snapshot.packages()) {
			sender.sendMessage((inspected.valid() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ")
					+ ChatColor.RESET + inspected.path());
			sender.sendMessage("   " + (inspected.valid()
					? "plugin=" + inspected.pluginId()
					: "error=" + inspected.error()));
		}
	}

	private static void metadata(CommandSender sender, BridgeManagementSnapshot snapshot, String[] args) {
		ManagedPluginSnapshot plugin = requiredPlugin(sender, snapshot, args, "metadata");
		if (plugin == null) {
			return;
		}
		var metadata = plugin.metadata();
		sender.sendMessage(PREFIX + metadata.name() + " [" + metadata.id() + "]");
		sender.sendMessage(" version=" + metadata.version() + ", ABI=" + metadata.apiVersion()
				+ ", environment=" + metadata.environment().name().toLowerCase(Locale.ROOT));
		sender.sendMessage(" state=" + plugin.state() + ", backend=" + plugin.backend());
		sender.sendMessage(" authors=" + String.join(", ", metadata.authors()));
		sender.sendMessage(" origin=" + plugin.origin());
		if (metadata.description() != null && !metadata.description().isBlank()) {
			sender.sendMessage(" " + metadata.description());
		}
		if (metadata.configSchema() != null) {
			sender.sendMessage(" schema=" + ProtocolJson.GSON.toJson(metadata.configSchema()));
		}
	}

	private static void logs(CommandSender sender, BridgeManagementSnapshot snapshot, String[] args) {
		ManagedPluginSnapshot plugin = requiredPlugin(sender, snapshot, args, "logs");
		if (plugin == null) {
			return;
		}
		int count = 20;
		if (args.length >= 3) {
			try {
				count = Math.clamp(Integer.parseInt(args[2]), 1, 100);
			} catch (NumberFormatException exception) {
				sender.sendMessage(PREFIX + ChatColor.RED + "Log count must be an integer from 1 to 100.");
				return;
			}
		}
		List<PluginLog> logs = plugin.logs();
		int from = Math.max(0, logs.size() - count);
		sender.sendMessage(PREFIX + "Last " + (logs.size() - from) + " log entries for " + plugin.metadata().id());
		for (PluginLog log : logs.subList(from, logs.size())) {
			sender.sendMessage(" [" + value(log.level()) + "/" + value(log.stream()) + "] " + value(log.message()));
		}
	}

	private void subscribe(CommandSender sender, String[] args) {
		ManagedPluginSnapshot plugin = requiredPlugin(
				sender, this.plugins.managementSnapshot(null), args, "subscribe"
		);
		if (plugin == null) {
			return;
		}
		String pluginId = plugin.metadata().id();
		boolean added;
		synchronized (this) {
			added = this.logSubscribers.computeIfAbsent(pluginId, ignored -> new HashSet<>()).add(sender);
		}
		sender.sendMessage(PREFIX + (added
				? ChatColor.GREEN + "Subscribed to live output from " + pluginId + "."
				: ChatColor.YELLOW + "Already subscribed to live output from " + pluginId + "."));
	}

	private synchronized void unsubscribe(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gbm unsubscribe <plugin-id|all>");
			return;
		}
		if (args[1].equalsIgnoreCase("all")) {
			int removed = unsubscribeAll(sender);
			sender.sendMessage(PREFIX + ChatColor.GREEN
					+ "Removed " + removed + " live output subscription(s).");
			return;
		}
		Set<CommandSender> subscribers = this.logSubscribers.get(args[1]);
		boolean removed = subscribers != null && subscribers.remove(sender);
		if (subscribers != null && subscribers.isEmpty()) {
			this.logSubscribers.remove(args[1]);
		}
		sender.sendMessage(PREFIX + (removed
				? ChatColor.GREEN + "Unsubscribed from live output from " + args[1] + "."
				: ChatColor.YELLOW + "No live output subscription for " + args[1] + "."));
	}

	synchronized void disconnected(Player player) {
		unsubscribeAll(player);
	}

	private int unsubscribeAll(CommandSender sender) {
		int removed = 0;
		var iterator = this.logSubscribers.entrySet().iterator();
		while (iterator.hasNext()) {
			Set<CommandSender> subscribers = iterator.next().getValue();
			if (subscribers.remove(sender)) {
				removed++;
			}
			if (subscribers.isEmpty()) {
				iterator.remove();
			}
		}
		return removed;
	}

	private synchronized void publishLog(String pluginId, PluginLog log) {
		Set<CommandSender> subscribers = this.logSubscribers.get(pluginId);
		if (subscribers == null) {
			return;
		}
		String message = PREFIX + ChatColor.GRAY + "[" + pluginId + "] ["
				+ value(log.level()) + "/" + value(log.stream()) + "] " + value(log.message());
		for (CommandSender subscriber : List.copyOf(subscribers)) {
			if (subscriber instanceof Player player && !player.isOnline()) {
				subscribers.remove(subscriber);
				continue;
			}
			subscriber.sendMessage(message);
		}
		if (subscribers.isEmpty()) {
			this.logSubscribers.remove(pluginId);
		}
	}

	private void reload(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gbm reload <plugin-id>");
			return;
		}
		result(sender, this.plugins.reload(args[1]));
	}

	private void load(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gbm load <slug> [y|n]");
			return;
		}
		String slug = args[1].toLowerCase(Locale.ROOT);
		if (slug.length() < 2 || slug.length() > 64 || !slug.matches(SLUG_PATTERN)) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Project slug must be 2-64 lowercase letters, digits, or hyphen-separated words.");
			return;
		}
		if (args.length == 2) {
			findInstall(sender, slug);
			return;
		}
		confirmInstall(sender, slug, args[2]);
	}

	private void findInstall(CommandSender sender, String slug) {
		String senderKey = senderKey(sender);
		if (!this.busySenders.add(senderKey)) {
			sender.sendMessage(PREFIX + ChatColor.YELLOW + "A catalog operation is already running for you.");
			return;
		}
		this.pendingInstalls.remove(senderKey);
		sender.sendMessage(PREFIX + "Searching the catalog for " + slug + "...");
		this.paperPlugin.getServer().getScheduler().runTaskAsynchronously(this.paperPlugin, () -> {
			try {
				GbmCatalogService.InstallCandidate candidate = this.catalog.findBySlug(slug);
				sync(() -> foundInstall(sender, senderKey, candidate));
			} catch (IOException | RuntimeException exception) {
				this.paperPlugin.getLogger().log(Level.WARNING, "Cannot find GBM catalog project " + slug, exception);
				sync(() -> operationFailed(sender, senderKey, "Catalog lookup failed: " + rootMessage(exception)));
			}
		});
	}

	private void foundInstall(
			CommandSender sender,
			String senderKey,
			GbmCatalogService.InstallCandidate candidate
	) {
		this.busySenders.remove(senderKey);
		this.pendingInstalls.put(senderKey, new PendingInstall(
				candidate, Instant.now().toEpochMilli() + CONFIRMATION_TTL_MILLIS
		));
		CatalogProject project = candidate.project();
		CatalogVersion version = candidate.version();
		String authors = version.metadata().authors().isEmpty()
				? "unknown"
				: String.join(", ", version.metadata().authors());
		sender.sendMessage(PREFIX + "Found " + project.name() + " [" + project.slug() + "]");
		sender.sendMessage(" author=" + authors + ", version=" + version.version()
				+ ", channel=" + channel(version.tag()));
		if (!project.description().isBlank()) {
			sender.sendMessage(" " + project.description());
		}
		sender.sendMessage(Component.text("Install this package? ", NamedTextColor.AQUA)
				.append(Component.text("[y]", NamedTextColor.GREEN)
						.clickEvent(ClickEvent.runCommand("/gbm load " + project.slug() + " y"))
						.hoverEvent(HoverEvent.showText(Component.text("Download and install"))))
				.append(Component.space())
				.append(Component.text("[n]", NamedTextColor.RED)
						.clickEvent(ClickEvent.runCommand("/gbm load " + project.slug() + " n"))
						.hoverEvent(HoverEvent.showText(Component.text("Abort")))));
	}

	private void confirmInstall(CommandSender sender, String slug, String answer) {
		String senderKey = senderKey(sender);
		PendingInstall pending = this.pendingInstalls.get(senderKey);
		if (pending == null
				|| pending.expiresAtUnixMilli() < Instant.now().toEpochMilli()
				|| !pending.candidate().project().slug().equalsIgnoreCase(slug)) {
			this.pendingInstalls.remove(senderKey);
			sender.sendMessage(PREFIX + ChatColor.RED + "No matching confirmation is pending. Run /gbm load " + slug + " again.");
			return;
		}

		String normalized = answer.toLowerCase(Locale.ROOT);
		if (normalized.equals("n") || normalized.equals("no")) {
			this.pendingInstalls.remove(senderKey);
			sender.sendMessage(PREFIX + "Abort downloading.");
			return;
		}
		if (!normalized.equals("y") && !normalized.equals("yes")) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Choose y or n.");
			return;
		}
		if (!this.busySenders.add(senderKey)) {
			sender.sendMessage(PREFIX + ChatColor.YELLOW + "A catalog operation is already running for you.");
			return;
		}

		this.pendingInstalls.remove(senderKey);
		boolean reinstall = this.catalog.installedPackages().stream()
				.anyMatch(installed -> installed.projectId().equals(pending.candidate().project().id()));
		sender.sendMessage(PREFIX + (reinstall ? "Reinstalling " : "Downloading ") + slug + "...");
		this.paperPlugin.getServer().getScheduler().runTaskAsynchronously(this.paperPlugin, () -> {
			try {
				InstalledCatalogPackage installed;
				synchronized (this.catalog) {
					installed = this.catalog.install(pending.candidate());
				}
				sync(() -> installed(sender, senderKey, installed, reinstall));
			} catch (IOException | RuntimeException exception) {
				this.paperPlugin.getLogger().log(Level.WARNING, "Cannot install GBM catalog project " + slug, exception);
				sync(() -> operationFailed(sender, senderKey, "Installation failed: " + rootMessage(exception)));
			}
		});
	}

	private void installed(
			CommandSender sender,
			String senderKey,
			InstalledCatalogPackage installed,
			boolean reinstall
	) {
		Path binary;
		try {
			binary = this.catalog.installedBinary(installed);
		} catch (IOException exception) {
			operationFailed(sender, senderKey, "Installation state is invalid: " + rootMessage(exception));
			return;
		}
		if (reinstall) {
			this.busySenders.remove(senderKey);
			sender.sendMessage(PREFIX + ChatColor.GREEN + "Reinstalled " + installed.slug() + " " + installed.version() + ".");
			sender.sendMessage(PREFIX + ChatColor.YELLOW
					+ "Please fully restart the server to use the downloaded binary.");
			return;
		}

		PaperGoPluginManager.InstalledPluginLoadResult load = this.plugins.loadInstalled(binary);
		if (!load.loaded()) {
			this.busySenders.remove(senderKey);
			sender.sendMessage(PREFIX + (load.restartRequired() ? ChatColor.YELLOW : ChatColor.RED) + load.message());
			return;
		}
		this.paperPlugin.getServer().getScheduler().runTaskAsynchronously(this.paperPlugin, () -> {
			try {
				this.catalog.associatePlugin(binary, load.pluginId());
				sync(() -> {
					this.busySenders.remove(senderKey);
					sender.sendMessage(PREFIX + ChatColor.GREEN + load.message());
				});
			} catch (IOException exception) {
				this.paperPlugin.getLogger().log(Level.WARNING,
						"Cannot associate installed catalog package with " + load.pluginId(), exception);
				sync(() -> operationFailed(sender, senderKey,
						"Plugin loaded, but its catalog metadata could not be saved: " + rootMessage(exception)));
			}
		});
	}

	private void operationFailed(CommandSender sender, String senderKey, String message) {
		this.busySenders.remove(senderKey);
		sender.sendMessage(PREFIX + ChatColor.RED + message);
	}

	private void sync(Runnable operation) {
		this.paperPlugin.getServer().getScheduler().runTask(this.paperPlugin, operation);
	}

	private static ManagedPluginSnapshot requiredPlugin(
			CommandSender sender, BridgeManagementSnapshot snapshot, String[] args, String action
	) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gbm " + action + " <plugin-id>");
			return null;
		}
		return snapshot.plugins().stream()
				.filter(plugin -> plugin.metadata().id().equals(args[1]))
				.findFirst()
				.orElseGet(() -> {
					sender.sendMessage(PREFIX + ChatColor.RED + "Unknown Go plugin: " + args[1]);
					return null;
				});
	}

	private static void result(CommandSender sender, ReloadResult result) {
		sender.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
	}

	private static void usage(CommandSender sender, String label) {
		sender.sendMessage(PREFIX + "Usage: /" + label
				+ " <status|packages|metadata <id>|logs <id> [count]|subscribe <id>"
				+ "|unsubscribe <id|all>|reload <id>|rescan|load <slug>>");
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	private static String channel(String value) {
		String channel = value(value);
		if (channel.startsWith("VERSION_TAG_")) {
			channel = channel.substring("VERSION_TAG_".length());
		}
		return channel.isBlank() ? "unknown" : channel.toLowerCase(Locale.ROOT);
	}

	private static String senderKey(CommandSender sender) {
		return sender instanceof Player player
				? player.getUniqueId().toString()
				: "sender:" + sender.getName().toLowerCase(Locale.ROOT);
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@Override
	public List<String> onTabComplete(
			CommandSender sender,
			Command command,
			String alias,
			String[] args
	) {
		if (sender instanceof Player player && !player.isOp()) {
			return List.of();
		}
		if (args.length == 1) {
			return matching(List.of(
					"status", "packages", "metadata", "logs", "subscribe", "unsubscribe",
					"reload", "rescan", "load"
			), args[0]);
		}
		if (args.length == 2 && List.of(
				"metadata", "logs", "subscribe", "unsubscribe", "reload"
		).contains(args[0].toLowerCase(Locale.ROOT))) {
			List<String> ids = this.plugins.managementSnapshot(null).plugins().stream()
					.map(plugin -> plugin.metadata().id())
					.toList();
			if (args[0].equalsIgnoreCase("unsubscribe")) {
				ids = new ArrayList<>(ids);
				ids.add("all");
			}
			return matching(ids, args[1]);
		}
		if (args.length == 3 && args[0].equalsIgnoreCase("load")) {
			return matching(List.of("y", "n"), args[2]);
		}
		return List.of();
	}

	private static List<String> matching(List<String> values, String prefix) {
		String lower = prefix.toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (String value : values) {
			if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
				result.add(value);
			}
		}
		return result;
	}

	private record PendingInstall(
			GbmCatalogService.InstallCandidate candidate,
			long expiresAtUnixMilli
	) {
	}
}
