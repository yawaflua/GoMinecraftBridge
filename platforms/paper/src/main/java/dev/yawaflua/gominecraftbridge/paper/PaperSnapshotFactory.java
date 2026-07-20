package dev.yawaflua.gominecraftbridge.paper;

import dev.yawaflua.gominecraftbridge.protocol.BlockReference;
import dev.yawaflua.gominecraftbridge.protocol.BlockSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.EntitySnapshot;
import dev.yawaflua.gominecraftbridge.protocol.LevelSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.SnapshotSubscription;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PaperSnapshotFactory {
	ServerSnapshot create(long tick, SnapshotSubscription subscription) {
		List<LevelSnapshot> levels = Bukkit.getWorlds().stream()
				.map(this::level)
				.toList();
		List<EntitySnapshot> entities = subscription.includesEntities()
				? Bukkit.getWorlds().stream()
						.flatMap(world -> world.getEntities().stream())
						.map(this::entity)
						.toList()
				: List.of();
		List<BlockSnapshot> blocks = new ArrayList<>();
		for (BlockReference reference : subscription.blocks()) {
			BlockSnapshot snapshot = block(reference);
			if (snapshot != null) {
				blocks.add(snapshot);
			}
		}
		return new ServerSnapshot(tick, Instant.now().toEpochMilli(), levels, entities, blocks);
	}

	EntitySnapshot entity(Entity entity) {
		var location = entity.getLocation();
		Vector velocity = entity.getVelocity();
		Float health = null;
		Float maxHealth = null;
		if (entity instanceof LivingEntity living) {
			health = (float) living.getHealth();
			// Kept deliberately: Attribute.GENERIC_MAX_HEALTH was renamed to
			// Attribute.MAX_HEALTH after 1.21.1, while this Bukkit method remains
			// binary-compatible across the three supported Paper lines.
			maxHealth = (float) living.getMaxHealth();
		}
		return new EntitySnapshot(
				entity.getEntityId(),
				entity.getUniqueId().toString(),
				entity.getType().getKey().toString(),
				entity.getName(),
				entity.getWorld().getKey().toString(),
				location.getX(), location.getY(), location.getZ(),
				location.getYaw(), location.getPitch(),
				velocity.getX(), velocity.getY(), velocity.getZ(),
				!entity.isDead(), entity instanceof Player, health, maxHealth
		);
	}

	private LevelSnapshot level(World world) {
		return new LevelSnapshot(
				world.getKey().toString(),
				world.getFullTime(),
				world.getTime(),
				world.hasStorm(),
				world.isThundering()
		);
	}

	private BlockSnapshot block(BlockReference reference) {
		NamespacedKey key = NamespacedKey.fromString(reference.dimension());
		World world = key == null ? null : Bukkit.getWorld(key);
		if (world == null || !world.isChunkLoaded(reference.x() >> 4, reference.z() >> 4)) {
			return null;
		}
		Block block = world.getBlockAt(reference.x(), reference.y(), reference.z());
		return new BlockSnapshot(
				reference.dimension(), reference.x(), reference.y(), reference.z(),
				block.getType().getKey().toString(), blockProperties(block.getBlockData().getAsString())
		);
	}

	private static Map<String, String> blockProperties(String blockData) {
		int open = blockData.indexOf('[');
		int close = blockData.lastIndexOf(']');
		if (open < 0 || close <= open + 1) {
			return Map.of();
		}
		Map<String, String> properties = new LinkedHashMap<>();
		for (String assignment : blockData.substring(open + 1, close).split(",")) {
			int equals = assignment.indexOf('=');
			if (equals > 0) {
				properties.put(assignment.substring(0, equals), assignment.substring(equals + 1));
			}
		}
		return Map.copyOf(properties);
	}
}
