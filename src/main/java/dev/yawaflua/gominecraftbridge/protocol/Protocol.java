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
		CLIENT_TICK(8);

		private final int code;

		Operation(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}
}
