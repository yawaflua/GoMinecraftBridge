package dev.yawaflua.gominecraftbridge.host;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import dev.yawaflua.gominecraftbridge.api.SystemCallRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BuiltInSystemCallsTest {
	@Test
	void registersEveryBuiltInSystemCallByItsEnumId() {
		SystemCallRegistry registry = new SystemCallRegistry();
		BuiltInSystemCalls.register(registry);

		assertEquals(
				Set.of(
						BuiltInSystemCall.SERVER_INFO.id(),
						BuiltInSystemCall.PLAYER_GET.id(),
						BuiltInSystemCall.BLOCK_GET.id(),
						BuiltInSystemCall.GET_ENTITY.id()
				),
				registry.entries().keySet()
		);
	}

	@Test
	void parsesUuidEntityLookup() {
		UUID uuid = UUID.fromString("a4b505b8-4379-42ce-aed1-192b7698b20f");
		var lookup = BuiltInSystemCalls.parseEntityLookup(
				JsonParser.parseString("{\"uuid\":\"" + uuid + "\"}")
		);

		assertEquals(uuid, lookup.uuid());
		assertNull(lookup.runtimeId());
	}

	@Test
	void parsesRuntimeIdEntityLookup() {
		var lookup = BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{\"runtimeId\":42}"));

		assertNull(lookup.uuid());
		assertEquals(42, lookup.runtimeId());
	}

	@Test
	void rejectsMissingAmbiguousOrMalformedSelectors() {
		assertThrows(IllegalArgumentException.class, () -> BuiltInSystemCalls.parseEntityLookup(JsonNull.INSTANCE));
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{}"))
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(
						JsonParser.parseString("{\"uuid\":\"a4b505b8-4379-42ce-aed1-192b7698b20f\",\"runtimeId\":42}")
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{\"uuid\":\"invalid\"}"))
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{\"uuid\":\"1-1-1-1-1\"}"))
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{\"runtimeId\":1.5}"))
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BuiltInSystemCalls.parseEntityLookup(JsonParser.parseString("{\"runtimeId\":2147483648}"))
		);
	}
}
