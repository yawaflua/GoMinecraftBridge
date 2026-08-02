package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenOption;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;

import java.util.HashSet;
import java.util.Set;

final class ClientScreenSpecValidator {
	private static final Set<String> FIELD_TYPES = Set.of("text", "number", "password", "select");
	private static final Set<String> ELEMENT_TYPES = Set.of(
			"text", "rectangle", "button", "hitbox", "text_input", "number_input", "password_input", "select"
	);
	private static final Set<String> ANCHORS = Set.of(
			"top_left", "top_center", "top_right", "center_left", "center", "center_right",
			"bottom_left", "bottom_center", "bottom_right"
	);

	private ClientScreenSpecValidator() {
	}

	static void validate(ClientScreenSpec screen) {
		if (screen == null) {
			throw new IllegalArgumentException("Screen is missing");
		}
		validateId(screen.id(), "screen");
		validateText(screen.title(), 256, "Screen title");
		validateText(screen.body(), 4_096, "Screen body");
		if (screen.elements().size() > 256 || screen.fields().size() > 32 || screen.buttons().size() > 16) {
			throw new IllegalArgumentException("Screen contains too many fields or buttons");
		}
		Set<String> ids = new HashSet<>();
		for (ClientScreenElement element : screen.elements()) {
			if (element == null) {
				throw new IllegalArgumentException("Screen element is missing");
			}
			validateId(element.id(), "element");
			if (!ids.add(element.id())) {
				throw new IllegalArgumentException("Duplicate screen component id: " + element.id());
			}
			if (!ELEMENT_TYPES.contains(element.type())) {
				throw new IllegalArgumentException("Unknown screen element type: " + element.type());
			}
			if (element.anchor() != null && !element.anchor().isBlank() && !ANCHORS.contains(element.anchor())) {
				throw new IllegalArgumentException("Unknown screen element anchor: " + element.anchor());
			}
			if (Math.abs(element.x()) > 32_768 || Math.abs(element.y()) > 32_768
					|| element.width() < 0 || element.width() > 32_768
					|| element.height() < 0 || element.height() > 32_768) {
				throw new IllegalArgumentException("Screen element bounds are out of range");
			}
			validateText(element.text(), 4_096, "Element text");
			validateText(element.placeholder(), 256, "Element placeholder");
			validateText(element.value(), 1_024, "Element value");
			if (element.maxLength() < 0 || element.maxLength() > 1_024 || element.options().size() > 64) {
				throw new IllegalArgumentException("Screen element input limits are out of range");
			}
			if (element.type().equals("select") && element.options().isEmpty()) {
				throw new IllegalArgumentException("Select element requires options");
			}
		}
		for (ClientScreenField field : screen.fields()) {
			if (field == null) {
				throw new IllegalArgumentException("Screen field is missing");
			}
			validateId(field.id(), "field");
			if (!ids.add(field.id())) {
				throw new IllegalArgumentException("Duplicate screen component id: " + field.id());
			}
			if (!FIELD_TYPES.contains(field.type())) {
				throw new IllegalArgumentException("Unknown screen field type: " + field.type());
			}
			validateText(field.label(), 128, "Field label");
			validateText(field.placeholder(), 256, "Field placeholder");
			validateText(field.value(), 1_024, "Field value");
			if (field.maxLength() < 0 || field.maxLength() > 1_024) {
				throw new IllegalArgumentException("Field maxLength is out of range");
			}
			if (field.options().size() > 64) {
				throw new IllegalArgumentException("Select field contains too many options");
			}
			for (ClientScreenOption option : field.options()) {
				if (option == null) {
					throw new IllegalArgumentException("Select option is missing");
				}
				validateText(option.value(), 1_024, "Option value");
				validateText(option.label(), 128, "Option label");
			}
			if (field.type().equals("select") && field.options().isEmpty()) {
				throw new IllegalArgumentException("Select field requires options");
			}
		}
		for (ClientScreenButton button : screen.buttons()) {
			if (button == null) {
				throw new IllegalArgumentException("Screen button is missing");
			}
			validateId(button.id(), "button");
			if (!ids.add(button.id())) {
				throw new IllegalArgumentException("Duplicate screen component id: " + button.id());
			}
			validateText(button.label(), 128, "Button label");
		}
	}

	private static void validateId(String id, String kind) {
		if (id == null || !id.matches("[A-Za-z0-9._-]{1,64}")) {
			throw new IllegalArgumentException("Invalid " + kind + " id: " + id);
		}
	}

	private static void validateText(String value, int maximum, String name) {
		if (value != null && value.length() > maximum) {
			throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
		}
	}
}
