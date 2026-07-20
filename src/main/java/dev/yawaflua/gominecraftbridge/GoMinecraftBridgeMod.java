package dev.yawaflua.gominecraftbridge;

import dev.yawaflua.gominecraftbridge.api.GoMinecraftBridgeApi;
import dev.yawaflua.gominecraftbridge.host.BuiltInSystemCalls;
import dev.yawaflua.gominecraftbridge.host.GoPluginManager;
import dev.yawaflua.gominecraftbridge.host.MinecraftSnapshotFactory;
import dev.yawaflua.gominecraftbridge.protocol.AfterDamageEvent;
import dev.yawaflua.gominecraftbridge.protocol.AllowDamageEvent;
import dev.yawaflua.gominecraftbridge.protocol.AllowDeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.MobConversionEvent;
import dev.yawaflua.gominecraftbridge.network.BridgeAdminNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public final class GoMinecraftBridgeMod implements ModInitializer {
	public static final String MOD_ID = "go_minecraft_bridge";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BuiltInSystemCalls.register(GoMinecraftBridgeApi.systemCalls());
		GoPluginManager plugins = new GoPluginManager(LOGGER);
		BridgeAdminNetworking.register(plugins);
		MinecraftSnapshotFactory snapshots = new MinecraftSnapshotFactory();

		// The common initializer also runs in a multiplayer client process. Its
		// client entrypoint discovers and starts client plugins immediately; this
		// server runtime waits until a real dedicated/integrated server exists.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> plugins.discover());
		ServerLifecycleEvents.SERVER_STARTED.register(plugins::start);
		ServerTickEvents.END_SERVER_TICK.register(plugins::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(plugins::stop);

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, boundChatType) -> plugins.chat(
				new ChatEvent(
						sender.getUUID().toString(),
						sender.getName().getString(),
						message.signedContent(),
						Instant.now().toEpochMilli()
				),
				sender.level().getServer()
		));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			return plugins.allowDamage(
					new AllowDamageEvent(
							snapshots.entity(entity),
							source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							amount,
							Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			var attacker = source.getEntity();
			plugins.afterDamage(
					new AfterDamageEvent(
							snapshots.entity(entity),
							source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							baseDamageTaken,
							damageTaken,
							blocked,
							Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
			var attacker = source.getEntity();
			return plugins.allowDeath(
					new AllowDeathEvent(
							snapshots.entity(entity),
							source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							damageAmount,
							Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			var attacker = source.getEntity();
			plugins.death(
					new DeathEvent(
							snapshots.entity(entity),
							source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});

		// The third callback parameter changed from a boolean to ConversionParams
		// in newer Fabric API versions. The common payload intentionally contains
		// only the stable before/after entity snapshots.
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, conversionContext) ->
				plugins.mobConversion(
						new MobConversionEvent(
								snapshots.entity(previous),
								snapshots.entity(converted),
								Instant.now().toEpochMilli()
						),
						converted.level().getServer()
				)
		);

		LOGGER.info("[GMB] GMB initialized");
	}
}
