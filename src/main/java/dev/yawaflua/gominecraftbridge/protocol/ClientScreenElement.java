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
		boolean close,
		boolean disabled,
		boolean notifyChanges,
		String tooltip,
		boolean wrap,
		boolean ellipsis,
		String align,
		List<ClientScreenListItem> items,
		int rowHeight,
		double min,
		double max,
		double step,
		String item,
		int count,
		String texture,
		int textureWidth,
		int textureHeight,
		int u,
		int v
) {
	public ClientScreenElement {
		options = options == null ? List.of() : List.copyOf(options);
		items = items == null ? List.of() : List.copyOf(items);
	}
}
