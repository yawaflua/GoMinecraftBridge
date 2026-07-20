package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.management.BridgeManagementSnapshot;
import dev.yawaflua.gominecraftbridge.management.PackageInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BridgeAdminNetworkingTest {
	@Test
	void deniedSnapshotDoesNotExposeManagementDetails() {
		BridgeManagementSnapshot source = new BridgeManagementSnapshot(
				123L,
				true,
				true,
				"Administrator permission is required",
				List.of(new PackageInspection("/secret/plugin.so", false, null, "load error")),
				List.of()
		);

		BridgeManagementSnapshot denied = BridgeAdminNetworking.withoutDetails(source);

		assertEquals(123L, denied.generatedAtUnixMilli());
		assertTrue(denied.serverRunning());
		assertFalse(denied.canReload());
		assertEquals("Administrator permission is required", denied.message());
		assertTrue(denied.packages().isEmpty());
		assertTrue(denied.plugins().isEmpty());
	}
}
