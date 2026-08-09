package dev.yawaflua.gominecraftbridge.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class BridgeCapabilities {
	private static final List<String> SERVER_COMMON = List.of(
			"gbm:action_results",
			"minecraft:event.server_tick",
			"minecraft:event.chat",
			"minecraft:event.chat.allow",
			"minecraft:event.player_join",
			"minecraft:event.player_disconnect",
			"minecraft:event.death",
			"minecraft:event.interaction",
			"minecraft:chat.broadcast",
			"minecraft:chat.player",
			"minecraft:server.info",
			"minecraft:player.get",
			"minecraft:block.get",
			"minecraft:entity.get",
			"minecraft:entity.kill"
	);
	private static final List<String> FABRIC_SERVER = append(SERVER_COMMON, List.of(
			"minecraft:event.damage.allow",
			"minecraft:event.damage.after",
			"minecraft:event.death.allow",
			"minecraft:event.mob_conversion"
	));
	private static final List<String> PAPER_SERVER = append(SERVER_COMMON, List.of(
			"minecraft:event.damage.after"
	));
	private static final List<String> CLIENT = List.of(
			"gbm:action_results",
			"minecraft:event.client_tick",
			"minecraft:event.client_key",
			"minecraft:event.client_chat",
			"minecraft:event.interaction",
			"minecraft:client.chat.display",
			"minecraft:client.hud",
			"minecraft:client.screen",
			"minecraft:client.screen.capture",
			"minecraft:client.browser.open",
			"minecraft:client.session.join",
			"minecraft:client.config.save"
	);

	private BridgeCapabilities() {
	}

	public static List<String> fabricServer(Collection<String> registeredSystemCalls) {
		return append(FABRIC_SERVER, registeredSystemCalls);
	}

	public static List<String> paperServer() {
		return PAPER_SERVER;
	}

	public static List<String> client() {
		return CLIENT;
	}

	private static List<String> append(Collection<String> base, Collection<String> extra) {
		var values = new LinkedHashSet<String>(base);
		values.addAll(extra);
		return List.copyOf(new ArrayList<>(values));
	}
}
