package dev.yawaflua.gominecraftbridge.protocol;

public record ClientItem(String id, int count, String name) {
	public static final ClientItem EMPTY = new ClientItem(null, 0, null);
}
