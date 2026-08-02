package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ClientScreenElement(
		String id,
		String type,
		int x,
		int y,
		int width,
		int height,
		String text,
		String placeholder,
		String value,
		int maxLength,
		List<ClientScreenOption> options,
		long color,
		boolean shadow,
		String anchor,
		boolean close
) {
	public ClientScreenElement {
		options = options == null ? List.of() : List.copyOf(options);
	}
}
