package dev.yawaflua.gominecraftbridge.host;

/** Names of the system calls supplied by the bridge itself. */
public enum BuiltInSystemCall {
	SERVER_INFO("minecraft:server.info"),
	PLAYER_GET("minecraft:player.get"),
	BLOCK_GET("minecraft:block.get"),
	GET_ENTITY("minecraft:get_entity");

	private final String id;

	BuiltInSystemCall(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
