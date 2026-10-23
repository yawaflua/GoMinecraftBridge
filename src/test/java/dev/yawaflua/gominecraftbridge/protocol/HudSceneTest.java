package dev.yawaflua.gominecraftbridge.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HudSceneTest {
	@Test
	void decodesUnsignedArgbColorsProducedByGo() {
		HudScene scene = ProtocolJson.GSON.fromJson(
				"{\"elements\":[{\"type\":\"text\",\"text\":\"hello\",\"color\":4294967295}]}",
				HudScene.class
		);

		assertEquals(0xffff_ffffL, scene.elements().getFirst().color());
	}

	@Test
	void decodesPanelStyleAndScreenFlags() {
		HudElementDto panel = ProtocolJson.GSON.fromJson(
				"{\"type\":\"panel\",\"text\":\"a\\nb\",\"background\":2415923216,\"padding\":6,"
						+ "\"lineSpacing\":2,\"accent\":4283826005,\"overScreens\":true,\"hideOnScreens\":true}",
				HudElementDto.class
		);

		assertEquals(0x9000_1010L, panel.background());
		assertEquals(0xff55_ff55L, panel.accent());
		assertEquals(6, panel.padding());
		assertEquals(2, panel.lineSpacing());
		assertEquals(true, panel.overScreens() && panel.hideOnScreens());
	}
}
