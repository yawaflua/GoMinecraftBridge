package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import dev.yawaflua.gominecraftbridge.protocol.ProtocolJson;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientScreenFeatureTest {
	@Test
	void validatesScreenIdsAndDuplicateComponents() {
		ClientScreenSpec custom = new ClientScreenSpec(
				"custom", "Custom", null,
				List.of(element("{\"id\":\"panel\",\"type\":\"rectangle\",\"width\":300,\"height\":180,\"color\":3759675424,\"anchor\":\"center\"}")),
				List.of(), List.of(), false
		);
		ClientScreenSpecValidator.validate(custom);

		ClientScreenSpec valid = new ClientScreenSpec(
				"payment", "Payment", "Body",
				List.of(),
				List.of(new ClientScreenField("amount", "number", "Amount", null, "", 16, List.of())),
				List.of(new ClientScreenButton("submit", "Submit", true)),
				false
		);
		ClientScreenSpecValidator.validate(valid);

		ClientScreenSpec duplicate = new ClientScreenSpec(
				"payment", "Payment", null,
				List.of(),
				List.of(new ClientScreenField("submit", "text", "Value", null, "", 16, List.of())),
				List.of(new ClientScreenButton("submit", "Submit", false)),
				false
		);
		assertThrows(IllegalArgumentException.class, () -> ClientScreenSpecValidator.validate(duplicate));
	}

	@Test
	void decodesElementFieldsWithGoJsonNames() {
		ClientScreenElement list = element("""
				{"id":"cards","type":"list","width":200,"height":60,"rowHeight":14,"notifyChanges":true,
				"disabled":true,"tooltip":"tip","wrap":true,"ellipsis":true,"align":"center",
				"items":[{"id":"a","text":"First","color":4294901760,"tooltip":"row"}]}
				""");
		assertEquals(14, list.rowHeight());
		assertTrue(list.notifyChanges());
		assertTrue(list.disabled());
		assertEquals("tip", list.tooltip());
		assertTrue(list.wrap() && list.ellipsis());
		assertEquals("center", list.align());
		assertEquals("row", list.items().getFirst().tooltip());
		assertEquals(0xffff0000L, list.items().getFirst().color());

		ClientScreenElement slider = element("{\"id\":\"s\",\"type\":\"slider\",\"min\":1,\"max\":5,\"step\":0.5}");
		assertEquals(5.0, slider.max());
		assertEquals(0.5, slider.step());

		ClientScreenElement image = element("""
				{"id":"i","type":"image","width":16,"height":16,"texture":"minecraft:textures/a.png",
				"textureWidth":64,"textureHeight":32,"u":8,"v":4,"item":"minecraft:stone","count":3}
				""");
		assertEquals(64, image.textureWidth());
		assertEquals(4, image.v());
		assertEquals(3, image.count());

		ClientScreenSpec scrollable = ProtocolJson.GSON.fromJson("{\"id\":\"x\",\"title\":\"t\",\"scroll\":true}", ClientScreenSpec.class);
		assertTrue(scrollable.scroll());
	}

	@Test
	void rejectsInvalidInteractiveElements() {
		ClientScreenSpecValidator.validateElements(List.of(
				element("{\"id\":\"l\",\"type\":\"list\",\"width\":10,\"height\":10,\"items\":[{\"id\":\"a\",\"text\":\"A\"}]}"),
				element("{\"id\":\"s\",\"type\":\"slider\",\"min\":0,\"max\":1}"),
				element("{\"id\":\"c\",\"type\":\"checkbox\",\"text\":\"Remember\"}"),
				element("{\"id\":\"it\",\"type\":\"item\",\"item\":\"minecraft:diamond\"}"),
				element("{\"id\":\"img\",\"type\":\"image\",\"width\":8,\"height\":8,\"texture\":\"minecraft:t.png\"}")
		));
		List<String> invalid = List.of(
				"{\"id\":\"l\",\"type\":\"list\",\"items\":[]}",
				"{\"id\":\"l\",\"type\":\"list\",\"width\":10,\"height\":10,\"items\":[{\"id\":\"a\"},{\"id\":\"a\"}]}",
				"{\"id\":\"s\",\"type\":\"slider\",\"min\":2,\"max\":1}",
				"{\"id\":\"it\",\"type\":\"item\"}",
				"{\"id\":\"img\",\"type\":\"image\",\"texture\":\"minecraft:t.png\"}",
				"{\"id\":\"t\",\"type\":\"text\",\"align\":\"justify\"}"
		);
		for (String json : invalid) {
			assertThrows(IllegalArgumentException.class, () -> ClientScreenSpecValidator.validateElement(element(json)), json);
		}
		ClientScreenElement text = element("{\"id\":\"same\",\"type\":\"text\"}");
		assertThrows(IllegalArgumentException.class, () -> ClientScreenSpecValidator.validateElements(List.of(text, text)));
	}

	@Test
	void validatesMenuButtonsBeforeRegistering() {
		ClientMenuButtons buttons = new ClientMenuButtons();
		assertThrows(IllegalArgumentException.class, () -> buttons.add(null, "id", "Label", "inventory", 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> buttons.add(null, "id", "Label", "pause", 0, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> buttons.add(null, "", "Label", "pause", 0, 0, 0));
		assertTrue(buttons.snapshot(ClientMenuButtons.PAUSE).isEmpty());
	}

	private static ClientScreenElement element(String json) {
		return ProtocolJson.GSON.fromJson(json, ClientScreenElement.class);
	}

	@Test
	void packsAbgrPixelsIntoGmbcRgba() {
		byte[] frame = ClientScreenCaptureController.pack(new ClientFramebufferPixels(
				1, 1, new int[]{0x44332211}, true
		));
		ByteBuffer header = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
		assertArrayEquals(new byte[]{'G', 'M', 'B', 'C'}, new byte[]{frame[0], frame[1], frame[2], frame[3]});
		assertEquals(1, header.getInt(8));
		assertEquals(1, header.getInt(12));
		assertEquals(4, header.getInt(16));
		assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44}, new byte[]{frame[24], frame[25], frame[26], frame[27]});
	}
}
