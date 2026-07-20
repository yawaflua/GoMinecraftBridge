package dev.yawaflua.gominecraftbridge.paper;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperSystemCallsTest {
	private final PaperSystemCalls calls = new PaperSystemCalls(new PaperSnapshotFactory());

	@Test
	void getEntityRequiresExactlyOneSelector() {
		assertThrows(IllegalArgumentException.class, () ->
				this.calls.execute("minecraft:get_entity", new JsonObject(), 0));

		JsonObject both = new JsonObject();
		both.addProperty("uuid", "00000000-0000-0000-0000-000000000001");
		both.addProperty("runtimeId", 1);
		assertThrows(IllegalArgumentException.class, () ->
				this.calls.execute("minecraft:get_entity", both, 0));
	}

	@Test
	void rejectsUnknownCallsAndMalformedPayloads() {
		assertThrows(IllegalArgumentException.class, () ->
				this.calls.execute("example:missing", JsonNull.INSTANCE, 0));
		assertThrows(IllegalArgumentException.class, () ->
				this.calls.execute("minecraft:block.get", JsonNull.INSTANCE, 0));
	}
}
