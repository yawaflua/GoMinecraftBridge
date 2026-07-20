package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.management.ReloadResult;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PaperAdminCommand implements CommandExecutor, TabCompleter {
	private static final String PREFIX = ChatColor.DARK_AQUA + "[GoBridge] " + ChatColor.RESET;
	private final PaperGoPluginManager plugins;

	PaperAdminCommand(PaperGoPluginManager plugins) {
		this.plugins = plugins;
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
		BridgeManagementSnapshot snapshot = this.plugins.managementSnapshot(null);
		switch (action) {
			case "status" -> status(sender, snapshot);
			case "packages" -> packages(sender, snapshot);
			case "metadata" -> metadata(sender, snapshot, args);
			case "logs" -> logs(sender, snapshot, args);
			case "reload" -> reload(sender, args);
			case "rescan" -> result(sender, this.plugins.rescan());
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

	private void reload(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gmb reload <plugin-id>");
			return;
		}
		result(sender, this.plugins.reload(args[1]));
	}

	private static ManagedPluginSnapshot requiredPlugin(
			CommandSender sender, BridgeManagementSnapshot snapshot, String[] args, String action
	) {
		if (args.length < 2) {
			sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /gmb " + action + " <plugin-id>");
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
				+ " <status|packages|metadata <id>|logs <id> [count]|reload <id>|rescan>");
	}

	private static String value(String value) {
		return value == null ? "" : value;
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
			return matching(List.of("status", "packages", "metadata", "logs", "reload", "rescan"), args[0]);
		}
		if (args.length == 2 && List.of("metadata", "logs", "reload").contains(args[0].toLowerCase(Locale.ROOT))) {
			List<String> ids = this.plugins.managementSnapshot(null).plugins().stream()
					.map(plugin -> plugin.metadata().id())
					.toList();
			return matching(ids, args[1]);
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
}
