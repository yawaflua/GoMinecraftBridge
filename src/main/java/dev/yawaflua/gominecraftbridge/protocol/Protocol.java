package dev.yawaflua.gominecraftbridge.protocol;

public final class Protocol {
	public static final int ABI_VERSION = 3;
	public static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;

	private Protocol() {
	}

	public enum Operation {
		METADATA(1),
		INIT(2),
		TICK(3),
		CHAT(4),
		DEATH(5),
		SYSTEM_CALL_RESULT(6),
		DEINIT(7),
		/** Client-process tick. */
		CLIENT_TICK(8),
		/** Replaces the configuration exposed through plugin metadata. */
		CONFIG_UPDATE(9),
		/** Lets a server plugin allow or deny damage before it is applied. */
		ALLOW_DAMAGE(10),
		/** Reports the damage that was actually applied to a living entity. */
		AFTER_DAMAGE(11),
		/** Lets a server plugin allow or deny a living entity's death. */
		ALLOW_DEATH(12),
		/** Reports a mob being replaced by its converted form. */
		MOB_CONVERSION(13),
		/** Reports a client-local form interaction. */
		CLIENT_SCREEN_EVENT(14),
		/** Delivers a binary GMBC framebuffer to a client plugin. */
		CLIENT_SCREEN_CAPTURE(15),
		/** Reports an observed block or entity interaction. */
		INTERACTION(16),
		/** Reports the outcome of an action carrying a request ID. */
		ACTION_RESULT(17),
		/** Reports that a player joined the server. */
		PLAYER_JOIN(18),
		/** Reports that a player disconnected from the server. */
		PLAYER_DISCONNECT(19),
		/** Lets a server plugin allow or deny a player chat message. */
		ALLOW_CHAT(20),
		/** Reports a registered client key binding press. */
		CLIENT_KEY(21);

		private final int code;

		Operation(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}
}
