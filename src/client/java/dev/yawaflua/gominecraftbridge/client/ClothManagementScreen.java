package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import dev.yawaflua.gominecraftbridge.protocol.PluginLog;
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

		BridgeManagementSnapshot snapshot = GoMinecraftBridgeClient.latest();
		Set<String> reloads = new LinkedHashSet<>();
		boolean[] rescan = {false};
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Go Minecraft Bridge"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory packages = builder.getOrCreateCategory(Component.literal("Пакеты"));

		if (snapshot == null) {
			packages.addEntry(entries.startTextDescription(Component.literal(
					"Подключитесь к серверу с Go Minecraft Bridge. Запрос состояния отправлен."
			)).build());
		} else {
			addOverview(packages, entries, snapshot, rescan);
			for (ManagedPluginSnapshot plugin : snapshot.plugins()) {
				addPluginCategory(builder, entries, plugin, snapshot.canReload(), reloads);
			}
		}

		builder.setSavingRunnable(() -> {
			if (rescan[0]) {
				GoMinecraftBridgeClient.requestRescan();
			}
			for (String pluginId : reloads) {
				GoMinecraftBridgeClient.requestReload(pluginId);
			}
			if (reloads.isEmpty() && !rescan[0]) {
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

	private static void addOverview(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			BridgeManagementSnapshot snapshot,
			boolean[] rescan
	) {
		category.addEntry(entries.startTextDescription(Component.literal(
				"Сервер: " + (snapshot.serverRunning() ? "работает" : "остановлен")
						+ " • пакетов: " + snapshot.packages().size()
						+ " • плагинов: " + snapshot.plugins().size()
		)).build());
		if (snapshot.message() != null && !snapshot.message().isBlank()) {
			category.addEntry(entries.startTextDescription(Component.literal(snapshot.message())).build());
		}

		for (PackageInspection inspected : snapshot.packages()) {
			String status = inspected.valid() ? "✓ корректен" : "✗ ошибка";
			String details = inspected.valid()
					? "plugin id: " + inspected.pluginId()
					: inspected.error();
			category.addEntry(entries.startTextDescription(Component.literal(
					status + " — " + inspected.path() + "\n" + details
			)).build());
		}
		if (snapshot.packages().isEmpty()) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Нативные пакеты не найдены в config/go-minecraft-bridge/plugins или mods."
			)).build());
		}
		category.addEntry(entries.startBooleanToggle(
					Component.literal("Проверить и подключить новые пакеты при сохранении"), false
			)
				.setDefaultValue(false)
				.setSaveConsumer(enabled -> rescan[0] = enabled)
				.setTooltip(Component.literal(
						"Повторно сканирует папки plugins и mods. Уже загруженные native-библиотеки не выгружаются."
				))
				.build());
	}

	private static void addPluginCategory(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ManagedPluginSnapshot plugin,
			boolean canReload,
			Set<String> reloads
	) {
		PluginMetadata metadata = plugin.metadata();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal(metadata.name()));
		category.addEntry(entries.startTextDescription(Component.literal(
				metadata.id() + " " + metadata.version() + " • " + plugin.state()
		)).build());
		category.addEntry(entries.startTextDescription(Component.literal(
				(metadata.description() == null ? "" : metadata.description())
		)).build());
		category.addEntry(entries.startTextDescription(Component.literal(
				"ABI/API: " + metadata.apiVersion()
						+ "\nАвторы: " + String.join(", ", metadata.authors())
						+ "\nСайт: " + value(metadata.website())
						+ "\nBackend: " + plugin.backend()
						+ "\nПуть: " + plugin.origin()
		)).build());

		if (metadata.configSchema() != null) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Config schema:\n" + ProtocolJson.GSON.toJson(metadata.configSchema())
			)).build());
		}

		category.addEntry(entries.startBooleanToggle(
					Component.literal("Перезапустить lifecycle при сохранении"), false
			)
				.setDefaultValue(false)
				.setSaveConsumer(enabled -> {
					if (enabled) {
						reloads.add(metadata.id());
					}
				})
				.setTooltip(Component.literal(
						"Вызывает Deinit → Init. Изменённый native-бинарник загружается только после рестарта JVM."
				))
				.build());

		List<PluginLog> logs = plugin.logs();
		int from = Math.max(0, logs.size() - VISIBLE_LOG_LINES);
		category.addEntry(entries.startTextDescription(Component.literal(
				"Последние логи (" + (logs.size() - from) + " из " + logs.size() + "):"
		)).build());
		for (PluginLog log : logs.subList(from, logs.size())) {
			category.addEntry(entries.startTextDescription(Component.literal(format(log))).build());
		}
		if (!canReload) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"Reload недоступен: нужны права администратора и запущенный сервер."
			)).build());
		}
	}

	private static String format(PluginLog log) {
		return "[" + LOG_TIME.format(Instant.ofEpochMilli(log.timestampUnixMilli())) + "]"
				+ " [" + value(log.level()) + "/" + value(log.stream()) + "] " + value(log.message());
	}

	private static String value(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
