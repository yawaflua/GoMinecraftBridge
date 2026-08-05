package dev.yawaflua.gominecraftbridge.fabric;

import dev.yawaflua.gominecraftbridge.host.GoPluginManager;
import dev.yawaflua.gominecraftbridge.host.MinecraftSnapshotFactory;
import dev.yawaflua.gominecraftbridge.protocol.AfterDamageEvent;
import dev.yawaflua.gominecraftbridge.protocol.AllowDamageEvent;
import dev.yawaflua.gominecraftbridge.protocol.AllowDeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.MobConversionEvent;
import dev.yawaflua.gominecraftbridge.protocol.PlayerConnectionEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;

import java.time.Instant;

/** Binds Fabric server events to the platform-neutral GBM plugin manager. */
public final class GbmFabricServerRuntime {
	private final GoPluginManager plugins;
	private final MinecraftSnapshotFactory snapshots = new MinecraftSnapshotFactory();

	public GbmFabricServerRuntime(Logger logger) {
		this.plugins = new GoPluginManager(logger);
	}

	public void register() {
		registerLifecycle();
		registerChat();
		registerPlayerConnections();
		registerInteractions();
		registerLivingEntityEvents();
	}

	private void registerInteractions() {
		GbmFabricInteractionAdapter.register(false, (event, player) ->
				this.plugins.interaction(event, player.level().getServer())
		);
	}

	private void registerLifecycle() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> this.plugins.discover());
		ServerLifecycleEvents.SERVER_STARTED.register(this.plugins::start);
		ServerTickEvents.END_SERVER_TICK.register(this.plugins::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(this.plugins::stop);
	}

	private void registerChat() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> this.plugins.allowChat(
				new ChatEvent(
						sender.getUUID().toString(),
						sender.getName().getString(),
						message.signedContent(),
						Instant.now().toEpochMilli()
				),
				sender.level().getServer()
		));
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, boundChatType) -> this.plugins.chat(
				new ChatEvent(
						sender.getUUID().toString(),
						sender.getName().getString(),
						message.signedContent(),
						Instant.now().toEpochMilli()
				),
				sender.level().getServer()
		));
	}

	private void registerPlayerConnections() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> this.plugins.playerJoin(
				new PlayerConnectionEvent(
						this.snapshots.entity(handler.getPlayer()),
						Instant.now().toEpochMilli()
				),
				server
		));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> this.plugins.playerDisconnect(
				new PlayerConnectionEvent(
						this.snapshots.entity(handler.getPlayer()),
						Instant.now().toEpochMilli()
				),
				server
		));
	}

	private void registerLivingEntityEvents() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			return this.plugins.allowDamage(
					new AllowDamageEvent(
							this.snapshots.entity(entity), source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							amount, Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			var attacker = source.getEntity();
			this.plugins.afterDamage(
					new AfterDamageEvent(
							this.snapshots.entity(entity), source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							baseDamage, damage, blocked, Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
			var attacker = source.getEntity();
			return this.plugins.allowDeath(
					new AllowDeathEvent(
							this.snapshots.entity(entity), source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							damage, Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			var attacker = source.getEntity();
			this.plugins.death(
					new DeathEvent(
							this.snapshots.entity(entity), source.getMsgId(),
							attacker == null ? null : attacker.getUUID().toString(),
							Instant.now().toEpochMilli()
					),
					entity.level().getServer()
			);
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) ->
				this.plugins.mobConversion(
						new MobConversionEvent(
								this.snapshots.entity(previous),
								this.snapshots.entity(converted),
								Instant.now().toEpochMilli()
						),
						converted.level().getServer()
				)
		);
	}
}
