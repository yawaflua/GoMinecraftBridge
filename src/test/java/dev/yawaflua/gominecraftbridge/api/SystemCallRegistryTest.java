package dev.yawaflua.gominecraftbridge.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemCallRegistryTest {
	@Test
	void requiresNamespacedUniqueNames() {
		SystemCallRegistry registry = new SystemCallRegistry();
		registry.register("example:test", (context, payload) -> payload);

		assertTrue(registry.find("example:test").isPresent());
		assertThrows(
				IllegalArgumentException.class,
				() -> registry.register("example:test", (context, payload) -> payload)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> registry.register("not-namespaced", (context, payload) -> payload)
		);
	}
}
