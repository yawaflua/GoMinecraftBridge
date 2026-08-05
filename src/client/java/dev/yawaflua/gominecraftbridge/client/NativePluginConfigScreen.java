package dev.yawaflua.gominecraftbridge.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.yawaflua.gominecraftbridge.management.ManagedPluginSnapshot;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds a Cloth Config screen from a Go struct or a JSON Schema object. */
final class NativePluginConfigScreen {
	private NativePluginConfigScreen() {
	}

	static Screen create(Screen parent, String pluginId) {
		ManagedPluginSnapshot plugin = GoMinecraftBridgeClient.runtime().localPlugin(pluginId);
		if (plugin == null || plugin.metadata().configSchema() == null) {
			return parent;
		}

		JsonObject exposed = plugin.metadata().configSchema().deepCopy();
		boolean jsonSchema = isJsonSchema(exposed);
		JsonObject edited = jsonSchema ? new JsonObject() : exposed.deepCopy();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal(plugin.metadata().name() + " configuration"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("Configuration"));
		int count = jsonSchema
				? addSchemaObject(category, entries, exposed.getAsJsonObject("properties"), edited, "")
				: addObject(category, entries, exposed, edited, "");
		if (count == 0) {
			category.addEntry(entries.startTextDescription(Component.literal(
					"No supported boolean, string, numeric, or primitive-list fields were exposed."
			)).build());
		}
		builder.setSavingRunnable(() -> GoMinecraftBridgeClient.runtime()
				.updateLocalConfig(pluginId, edited.deepCopy()));
		return builder.build();
	}

	private static boolean isJsonSchema(JsonObject object) {
		return object.has("properties")
				&& object.get("properties").isJsonObject()
				&& (!object.has("type") || "object".equals(string(object.get("type"))));
	}

	private static int addObject(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			JsonObject source,
			JsonObject edited,
			String prefix
	) {
		int count = 0;
		for (var field : source.entrySet()) {
			String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
			JsonElement value = field.getValue();
			if (value.isJsonObject()) {
				JsonObject child = edited.getAsJsonObject(field.getKey());
				count += addObject(category, entries, value.getAsJsonObject(), child, path);
			} else {
				count += addValue(category, entries, Component.literal(path), value, null,
						newValue -> edited.add(field.getKey(), newValue));
			}
		}
		return count;
	}

	private static int addSchemaObject(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			JsonObject properties,
			JsonObject edited,
			String prefix
	) {
		int count = 0;
		for (var field : properties.entrySet()) {
			if (!field.getValue().isJsonObject()) {
				continue;
			}
			JsonObject schema = field.getValue().getAsJsonObject();
			String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
			String title = schema.has("title") ? string(schema.get("title")) : path;
			String type = schema.has("type") ? string(schema.get("type")) : null;
			if ("object".equals(type) && schema.has("properties") && schema.get("properties").isJsonObject()) {
				JsonObject child = new JsonObject();
				edited.add(field.getKey(), child);
				count += addSchemaObject(category, entries, schema.getAsJsonObject("properties"), child, path);
				continue;
			}

			JsonElement initial = schema.has("default") ? schema.get("default").deepCopy() : defaultValue(schema);
			edited.add(field.getKey(), initial.deepCopy());
			if (schema.has("enum") && schema.get("enum").isJsonArray()) {
				List<JsonPrimitive> choices = primitives(schema.getAsJsonArray("enum"));
				if (!choices.isEmpty()) {
					JsonPrimitive current = initial.isJsonPrimitive()
							&& choices.contains(initial.getAsJsonPrimitive())
							? initial.getAsJsonPrimitive()
							: choices.getFirst();
					category.addEntry(entries.startSelector(
							Component.literal(title), choices.toArray(JsonPrimitive[]::new), current
					).setNameProvider(value -> Component.literal(value.getAsString()))
							.setDefaultValue(current)
							.setTooltipSupplier(value -> fullStringTooltip(value.getAsString()))
							.setSaveConsumer(value -> edited.add(field.getKey(), value.deepCopy()))
							.build());
					count++;
					continue;
				}
			}
			String valueType = type;
			if ("array".equals(type) && schema.has("items") && schema.get("items").isJsonObject()) {
				valueType += ":" + string(schema.getAsJsonObject("items").get("type"));
			}
			count += addValue(category, entries, Component.literal(title), initial, valueType,
					newValue -> edited.add(field.getKey(), newValue));
		}
		return count;
	}

	private static JsonElement defaultValue(JsonObject schema) {
		String type = schema.has("type") ? string(schema.get("type")) : "string";
		return switch (type) {
			case "boolean" -> new JsonPrimitive(false);
			case "integer" -> new JsonPrimitive(0L);
			case "number" -> new JsonPrimitive(0.0D);
			case "array" -> new JsonArray();
			default -> new JsonPrimitive("");
		};
	}

	private static int addValue(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			Component name,
			JsonElement value,
			String declaredType,
			java.util.function.Consumer<JsonElement> save
	) {
		if (value == null || value.isJsonNull()) {
			return 0;
		}
		if (value.isJsonPrimitive()) {
			JsonPrimitive primitive = value.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				boolean current = primitive.getAsBoolean();
				category.addEntry(entries.startBooleanToggle(name, current)
						.setDefaultValue(current)
						.setSaveConsumer(updated -> save.accept(new JsonPrimitive(updated)))
						.build());
				return 1;
			}
			if (primitive.isString()) {
				String current = primitive.getAsString();
				category.addEntry(entries.startStrField(name, current)
						.setDefaultValue(current)
						.setTooltipSupplier(NativePluginConfigScreen::fullStringTooltip)
						.setSaveConsumer(updated -> save.accept(new JsonPrimitive(updated)))
						.build());
				return 1;
			}
			if (primitive.isNumber()) {
				BigDecimal number = primitive.getAsBigDecimal();
				if ("integer".equals(declaredType)
						|| (!"number".equals(declaredType) && number.stripTrailingZeros().scale() <= 0)) {
					long current = number.longValue();
					category.addEntry(entries.startLongField(name, current)
							.setDefaultValue(current)
							.setSaveConsumer(updated -> save.accept(new JsonPrimitive(updated)))
							.build());
				} else {
					double current = number.doubleValue();
					category.addEntry(entries.startDoubleField(name, current)
							.setDefaultValue(current)
							.setSaveConsumer(updated -> save.accept(new JsonPrimitive(updated)))
							.build());
				}
				return 1;
			}
		}
		if (value.isJsonArray()) {
			return addList(category, entries, name, value.getAsJsonArray(), declaredType, save);
		}
		return 0;
	}

	private static int addList(
			ConfigCategory category,
			ConfigEntryBuilder entries,
			Component name,
			JsonArray value,
			String declaredType,
			java.util.function.Consumer<JsonElement> save
	) {
		if ("array:string".equals(declaredType)
				|| (value.isEmpty() && (declaredType == null || "array:".equals(declaredType)))
				|| (declaredType == null && all(value, JsonPrimitive::isString))) {
			List<String> current = strings(value);
			category.addEntry(entries.startStrList(name, current)
					.setDefaultValue(current)
					.setSaveConsumer(updated -> save.accept(array(updated)))
					.build());
			return 1;
		}
		if ("array:integer".equals(declaredType) || (declaredType == null
				&& all(value, primitive -> primitive.isNumber()
				&& primitive.getAsBigDecimal().stripTrailingZeros().scale() <= 0))) {
			List<Long> current = new ArrayList<>();
			for (JsonElement element : value) {
				current.add(element.getAsLong());
			}
			category.addEntry(entries.startLongList(name, current)
					.setDefaultValue(current)
					.setSaveConsumer(updated -> save.accept(array(updated)))
					.build());
			return 1;
		}
		if ("array:number".equals(declaredType) || (declaredType == null && all(value, JsonPrimitive::isNumber))) {
			List<Double> current = new ArrayList<>();
			for (JsonElement element : value) {
				current.add(element.getAsDouble());
			}
			category.addEntry(entries.startDoubleList(name, current)
					.setDefaultValue(current)
					.setSaveConsumer(updated -> save.accept(array(updated)))
					.build());
			return 1;
		}
		return 0;
	}

	private static boolean all(JsonArray array, java.util.function.Predicate<JsonPrimitive> predicate) {
		for (JsonElement element : array) {
			if (!element.isJsonPrimitive() || !predicate.test(element.getAsJsonPrimitive())) {
				return false;
			}
		}
		return true;
	}

	private static List<String> strings(JsonArray array) {
		List<String> values = new ArrayList<>();
		for (JsonElement element : array) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				values.add(element.getAsString());
			}
		}
		return values;
	}

	private static List<JsonPrimitive> primitives(JsonArray array) {
		List<JsonPrimitive> values = new ArrayList<>();
		for (JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				values.add(element.getAsJsonPrimitive());
			}
		}
		return values;
	}

	private static Optional<Component[]> fullStringTooltip(String value) {
		if (value == null || value.length() <= 32) {
			return Optional.empty();
		}
		return Optional.of(new Component[]{Component.literal(value)});
	}

	private static JsonArray array(List<?> values) {
		JsonArray array = new JsonArray();
		for (Object value : values) {
			if (value instanceof Number number) {
				array.add(number);
			} else {
				array.add(String.valueOf(value));
			}
		}
		return array;
	}

	private static String string(JsonElement value) {
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
