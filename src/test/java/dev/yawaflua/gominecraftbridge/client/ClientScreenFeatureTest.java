package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ClientScreenFeatureTest {
	@Test
	void validatesScreenIdsAndDuplicateComponents() {
		ClientScreenSpec custom = new ClientScreenSpec(
				"custom", "Custom", null,
				List.of(new ClientScreenElement(
						"panel", "rectangle", 0, 0, 300, 180,
						null, null, null, 0, List.of(), 0xe0181820L, false, "center", false
				)),
				List.of(), List.of()
		);
		ClientScreenSpecValidator.validate(custom);

		ClientScreenSpec valid = new ClientScreenSpec(
				"payment", "Payment", "Body",
				List.of(),
				List.of(new ClientScreenField("amount", "number", "Amount", null, "", 16, List.of())),
				List.of(new ClientScreenButton("submit", "Submit", true))
		);
		ClientScreenSpecValidator.validate(valid);

		ClientScreenSpec duplicate = new ClientScreenSpec(
				"payment", "Payment", null,
				List.of(),
				List.of(new ClientScreenField("submit", "text", "Value", null, "", 16, List.of())),
				List.of(new ClientScreenButton("submit", "Submit", false))
		);
		assertThrows(IllegalArgumentException.class, () -> ClientScreenSpecValidator.validate(duplicate));
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
