package dev.yawaflua.gominecraftbridge.protocol;

public final class Protocol {
	public static final int ABI_VERSION = 2;
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
		/** Client-process tick. Added as an optional ABI v2 operation. */
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
		CLIENT_SCREEN_CAPTURE(15);

		private final int code;

		Operation(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}
}
