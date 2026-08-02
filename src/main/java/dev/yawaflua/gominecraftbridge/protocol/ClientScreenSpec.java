package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ClientScreenSpec(
		String id,
		String title,
		String body,
		List<ClientScreenElement> elements,
		List<ClientScreenField> fields,
		List<ClientScreenButton> buttons
) {
	public ClientScreenSpec {
		elements = elements == null ? List.of() : List.copyOf(elements);
		fields = fields == null ? List.of() : List.copyOf(fields);
		buttons = buttons == null ? List.of() : List.copyOf(buttons);
	}
}
