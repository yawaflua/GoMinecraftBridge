package dev.yawaflua.gominecraftbridge.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BridgeCapabilitiesTest {
	@Test
	void paperAdvertisesImplementedDamageAndChatEvents() {
		assertTrue(BridgeCapabilities.paperServer().contains("minecraft:event.damage.after"));
		assertTrue(BridgeCapabilities.paperServer().contains("minecraft:event.chat.allow"));
		assertFalse(BridgeCapabilities.paperServer().contains("minecraft:event.damage.allow"));
	}
}
