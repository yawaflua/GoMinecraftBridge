package dev.yawaflua.gominecraftbridge.host;

import dev.yawaflua.gominecraftbridge.protocol.BlockReference;
import dev.yawaflua.gominecraftbridge.protocol.BlockSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.EntitySnapshot;
import dev.yawaflua.gominecraftbridge.protocol.LevelSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.ServerSnapshot;
import dev.yawaflua.gominecraftbridge.protocol.SnapshotSubscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinecraftSnapshotFactory {
	public ServerSnapshot create(MinecraftServer server, SnapshotSubscription subscription) {
		List<LevelSnapshot> levels = new ArrayList<>();
		List<EntitySnapshot> entities = new ArrayList<>();
		Map<String, ServerLevel> levelsById = new LinkedHashMap<>();

		for (ServerLevel level : server.getAllLevels()) {
			String dimension = dimension(level);
			levelsById.put(dimension, level);
			levels.add(new LevelSnapshot(
					dimension,
					level.getGameTime(),
					level.getDefaultClockTime(),
					level.isRaining(),
					level.isThundering()
			));

			if (subscription.includesEntities()) {
				for (Entity entity : level.getAllEntities()) {
					entities.add(entity(entity));
				}
			}
		}

		List<BlockSnapshot> blocks = new ArrayList<>();
		for (BlockReference reference : subscription.blocks()) {
			ServerLevel level = levelsById.get(reference.dimension());
			if (level == null) {
				continue;
			}

			BlockPos position = new BlockPos(reference.x(), reference.y(), reference.z());
			if (!level.isLoaded(position)) {
				continue;
			}

			BlockState state = level.getBlockState(position);
			blocks.add(new BlockSnapshot(
					reference.dimension(),
					reference.x(),
					reference.y(),
					reference.z(),
					BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
					properties(state)
			));
		}

		return new ServerSnapshot(
				server.getTickCount(),
				Instant.now().toEpochMilli(),
				levels,
				entities,
				blocks
		);
	}

	public EntitySnapshot entity(Entity entity) {
		Vec3 velocity = entity.getDeltaMovement();
		Float health = null;
		Float maxHealth = null;

		if (entity instanceof LivingEntity living) {
			health = living.getHealth();
			maxHealth = living.getMaxHealth();
		}

		return new EntitySnapshot(
				entity.getId(),
				entity.getUUID().toString(),
				BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
				entity.getName().getString(),
				dimension(entity.level()),
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				entity.getYRot(),
				entity.getXRot(),
				velocity.x,
				velocity.y,
				velocity.z,
				entity.isAlive(),
				entity instanceof Player,
				health,
				maxHealth
		);
	}

	private static String dimension(net.minecraft.world.level.Level level) {
		return level.dimension().identifier().toString();
	}

	private static Map<String, String> properties(BlockState state) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties()) {
			addProperty(result, state, property);
		}
		return result;
	}

	private static <T extends Comparable<T>> void addProperty(
			Map<String, String> target,
			BlockState state,
			Property<T> property
	) {
		target.put(property.getName(), property.getName(state.getValue(property)));
	}
}
