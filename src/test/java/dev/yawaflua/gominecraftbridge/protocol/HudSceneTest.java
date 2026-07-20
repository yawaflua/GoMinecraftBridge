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
}
