package dev.yawaflua.gominecraftbridge.protocol;

import java.util.List;

public record ClientScreenUpdate(String id, List<ClientScreenElement> elements, List<String> remove) {
	public ClientScreenUpdate {
		elements = elements == null ? List.of() : List.copyOf(elements);
		remove = remove == null ? List.of() : List.copyOf(remove);
	}
}
