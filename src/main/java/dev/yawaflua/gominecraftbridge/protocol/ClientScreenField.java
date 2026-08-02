package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ClientScreenField(
		String id,
		String type,
		String label,
		String placeholder,
		String value,
		int maxLength,
		List<ClientScreenOption> options
) {
	public ClientScreenField {
		options = options == null ? List.of() : List.copyOf(options);
	}
}
