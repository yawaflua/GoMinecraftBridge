package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.catalog.GbmCatalogService;
import dev.yawaflua.gominecraftbridge.protocol.AfterDamageEvent;
import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
import dev.yawaflua.gominecraftbridge.protocol.InteractionEvent;
import dev.yawaflua.gominecraftbridge.protocol.PlayerConnectionEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public final class PaperBridgePlugin extends JavaPlugin implements Listener {
	private final PaperSnapshotFactory snapshots = new PaperSnapshotFactory();
	private PaperGoPluginManager plugins;
	private GbmCatalogService catalog;
	private PaperAdminCommand administrator;
	private BukkitTask tickTask;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		try {
			this.catalog = new GbmCatalogService(
					getDataFolder().toPath(), GbmCatalogService.InstallationTarget.SERVER
			);
			this.catalog.saveSettings(
					getConfig().getString("catalog.backend-url", GbmCatalogService.DEFAULT_BACKEND_URL), false
			);
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalStateException("Cannot initialize the GBM server catalog", exception);
		}
		this.plugins = new PaperGoPluginManager(getLogger(), getDataFolder().toPath());
		this.plugins.discover();
		this.plugins.start();

		Bukkit.getPluginManager().registerEvents(this, this);
		PluginCommand command = getCommand("gbm");
		if (command == null) {
			throw new IllegalStateException("gbm command is missing from plugin.yml");
		}
		this.administrator = new PaperAdminCommand(this, this.plugins, this.catalog);
		command.setExecutor(this.administrator);
		command.setTabCompleter(this.administrator);
		this.tickTask = Bukkit.getScheduler().runTaskTimer(this, this.plugins::tick, 1L, 1L);

		getLogger().info("GBM Paper/Purpur runtime enabled");
	}

	@Override
	public void onDisable() {
		if (this.tickTask != null) {
			this.tickTask.cancel();
		}
		if (this.plugins != null) {
			this.plugins.stop();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onAllowChat(AsyncChatEvent event) {
		ChatEvent chat = chatEvent(event);
		if (!allowChat(chat, event.isAsynchronous())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event) {
		ChatEvent chat = chatEvent(event);
		if (event.isAsynchronous()) {
			Bukkit.getScheduler().runTask(this, () -> this.plugins.chat(chat));
		} else {
			this.plugins.chat(chat);
		}
	}

	private static ChatEvent chatEvent(AsyncChatEvent event) {
		return new ChatEvent(
				event.getPlayer().getUniqueId().toString(),
				event.getPlayer().getName(),
				event.signedMessage().message(),
				Instant.now().toEpochMilli()
		);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAfterDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity entity)) {
			return;
		}
		double baseDamage = event.getDamage();
		double finalDamage = event.getFinalDamage();
		this.plugins.afterDamage(new AfterDamageEvent(
				this.snapshots.entity(entity), event.getCause().name().toLowerCase(), attacker(event),
				(float) baseDamage, (float) finalDamage, baseDamage > 0 && finalDamage <= 0,
				Instant.now().toEpochMilli()
		));
	}

	private boolean allowChat(ChatEvent event, boolean asynchronous) {
		if (!asynchronous) {
			return this.plugins.allowChat(event);
		}
		Future<Boolean> decision = Bukkit.getScheduler().callSyncMethod(
				this, () -> this.plugins.allowChat(event)
		);
		try {
			return decision.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			decision.cancel(false);
			getLogger().log(Level.WARNING, "Interrupted while checking a GBM chat decision; allowing message", exception);
		} catch (ExecutionException | TimeoutException exception) {
			decision.cancel(false);
			getLogger().log(Level.WARNING, "Cannot check a GBM chat decision; allowing message", exception);
		}
		return true;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		this.plugins.playerJoin(new PlayerConnectionEvent(
				this.snapshots.entity(event.getPlayer()), Instant.now().toEpochMilli()
		));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (this.administrator != null) {
			this.administrator.disconnected(event.getPlayer());
		}
		this.plugins.playerDisconnect(new PlayerConnectionEvent(
				this.snapshots.entity(event.getPlayer()), Instant.now().toEpochMilli()
		));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDeath(EntityDeathEvent event) {
		EntityDamageEvent damage = event.getEntity().getLastDamageCause();
		String damageType = damage == null ? "unknown" : damage.getCause().name().toLowerCase();
		String attackerUuid = attacker(damage);
		this.plugins.death(new DeathEvent(
				this.snapshots.entity(event.getEntity()), damageType, attackerUuid,
				Instant.now().toEpochMilli()
		));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockInteraction(PlayerInteractEvent event) {
		if (event.getClickedBlock() == null) {
			return;
		}
		String action = switch (event.getAction()) {
			case RIGHT_CLICK_BLOCK -> "use_block";
			case LEFT_CLICK_BLOCK -> "attack_block";
			default -> null;
		};
		if (action == null) {
			return;
		}
		this.plugins.interaction(new InteractionEvent(
				action, hand(event.getHand()), event.getPlayer().isSneaking(),
				this.snapshots.entity(event.getPlayer()), this.snapshots.block(event.getClickedBlock()), null,
				event.getBlockFace().name().toLowerCase(), null, null, null,
				Instant.now().toEpochMilli()
		));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityInteraction(PlayerInteractEntityEvent event) {
		this.plugins.interaction(new InteractionEvent(
				"use_entity", hand(event.getHand()), event.getPlayer().isSneaking(),
				this.snapshots.entity(event.getPlayer()), null, this.snapshots.entity(event.getRightClicked()),
				null, null, null, null, Instant.now().toEpochMilli()
		));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityAttack(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof org.bukkit.entity.Player player)) {
			return;
		}
		this.plugins.interaction(new InteractionEvent(
				"attack_entity", "main_hand", player.isSneaking(), this.snapshots.entity(player),
				null, this.snapshots.entity(event.getEntity()), null, null, null, null,
				Instant.now().toEpochMilli()
		));
	}

	private static String hand(EquipmentSlot slot) {
		return slot == EquipmentSlot.OFF_HAND ? "off_hand" : "main_hand";
	}

	private static String attacker(EntityDamageEvent damage) {
		if (!(damage instanceof EntityDamageByEntityEvent entityDamage)) {
			return null;
		}
		Entity damager = entityDamage.getDamager();
		if (damager instanceof Projectile projectile) {
			ProjectileSource shooter = projectile.getShooter();
			if (shooter instanceof Entity entity) {
				damager = entity;
			}
		}
		return damager.getUniqueId().toString();
	}
}
