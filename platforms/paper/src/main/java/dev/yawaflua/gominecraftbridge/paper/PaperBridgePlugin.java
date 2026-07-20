package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.protocol.ChatEvent;
import dev.yawaflua.gominecraftbridge.protocol.DeathEvent;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;

public final class PaperBridgePlugin extends JavaPlugin implements Listener {
	private final PaperSnapshotFactory snapshots = new PaperSnapshotFactory();
	private PaperGoPluginManager plugins;
	private PaperAdminMessaging adminMessaging;
	private BukkitTask tickTask;

	@Override
	public void onEnable() {
		this.plugins = new PaperGoPluginManager(getLogger(), getDataFolder().toPath());
		this.plugins.discover();
		this.plugins.start();

		Bukkit.getPluginManager().registerEvents(this, this);
		PluginCommand command = getCommand("go-minecraft-bridge");
		if (command == null) {
			throw new IllegalStateException("go-minecraft-bridge command is missing from plugin.yml");
		}
		PaperAdminCommand administrator = new PaperAdminCommand(this.plugins);
		command.setExecutor(administrator);
		command.setTabCompleter(administrator);
		this.adminMessaging = new PaperAdminMessaging(this, this.plugins);
		getServer().getMessenger().registerIncomingPluginChannel(
				this, PaperAdminMessaging.REQUEST_CHANNEL, this.adminMessaging
		);
		getServer().getMessenger().registerOutgoingPluginChannel(
				this, PaperAdminMessaging.RESPONSE_CHANNEL
		);
		this.tickTask = Bukkit.getScheduler().runTaskTimer(this, this.plugins::tick, 1L, 1L);

		getLogger().info("Go Minecraft Bridge Paper/Purpur runtime enabled");
	}

	@Override
	public void onDisable() {
		if (this.tickTask != null) {
			this.tickTask.cancel();
		}
		if (this.plugins != null) {
			this.plugins.stop();
		}
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		ChatEvent chat = new ChatEvent(
				event.getPlayer().getUniqueId().toString(),
				event.getPlayer().getName(),
				event.getMessage(),
				Instant.now().toEpochMilli()
		);
		if (event.isAsynchronous()) {
			Bukkit.getScheduler().runTask(this, () -> this.plugins.chat(chat));
		} else {
			this.plugins.chat(chat);
		}
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
