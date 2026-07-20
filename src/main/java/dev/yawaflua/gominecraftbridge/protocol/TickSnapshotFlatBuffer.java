package dev.yawaflua.gominecraftbridge.protocol;

import com.google.flatbuffers.FlatBufferBuilder;

import java.util.Map;

public final class TickSnapshotFlatBuffer {
	private TickSnapshotFlatBuffer() {
	}

	public static byte[] encode(ServerSnapshot snapshot) {
		FlatBufferBuilder builder = new FlatBufferBuilder(16 * 1024);

		int[] levelOffsets = new int[snapshot.levels().size()];
		for (int index = 0; index < snapshot.levels().size(); index++) {
			LevelSnapshot level = snapshot.levels().get(index);
			levelOffsets[index] = gmb.LevelSnapshot.createLevelSnapshot(
					builder,
					builder.createSharedString(level.dimension()),
					level.gameTime(),
					level.dayTime(),
					level.raining(),
					level.thundering()
			);
		}

		int[] entityOffsets = new int[snapshot.entities().size()];
		for (int index = 0; index < snapshot.entities().size(); index++) {
			EntitySnapshot entity = snapshot.entities().get(index);
			boolean hasHealth = entity.health() != null && entity.maxHealth() != null;
			entityOffsets[index] = gmb.EntitySnapshot.createEntitySnapshot(
					builder,
					entity.runtimeId(),
					builder.createSharedString(entity.uuid()),
					builder.createSharedString(entity.type()),
					builder.createSharedString(entity.name()),
					builder.createSharedString(entity.dimension()),
					entity.x(),
					entity.y(),
					entity.z(),
					entity.yaw(),
					entity.pitch(),
					entity.velocityX(),
					entity.velocityY(),
					entity.velocityZ(),
					entity.alive(),
					entity.player(),
					hasHealth,
					hasHealth ? entity.health() : 0.0F,
					hasHealth ? entity.maxHealth() : 0.0F
			);
		}

		int[] blockOffsets = new int[snapshot.blocks().size()];
		for (int index = 0; index < snapshot.blocks().size(); index++) {
			BlockSnapshot block = snapshot.blocks().get(index);
			int[] propertyOffsets = new int[block.properties().size()];
			int propertyIndex = 0;
			for (Map.Entry<String, String> property : block.properties().entrySet()) {
				propertyOffsets[propertyIndex++] = gmb.BlockProperty.createBlockProperty(
						builder,
						builder.createSharedString(property.getKey()),
						builder.createSharedString(property.getValue())
				);
			}
			int properties = gmb.BlockSnapshot.createPropertiesVector(builder, propertyOffsets);
			blockOffsets[index] = gmb.BlockSnapshot.createBlockSnapshot(
					builder,
					builder.createSharedString(block.dimension()),
					block.x(),
					block.y(),
					block.z(),
					builder.createSharedString(block.block()),
					properties
			);
		}

		int levels = gmb.ServerSnapshot.createLevelsVector(builder, levelOffsets);
		int entities = gmb.ServerSnapshot.createEntitiesVector(builder, entityOffsets);
		int blocks = gmb.ServerSnapshot.createBlocksVector(builder, blockOffsets);
		int root = gmb.ServerSnapshot.createServerSnapshot(
				builder,
				snapshot.tick(),
				snapshot.timestampUnixMilli(),
				levels,
				entities,
				blocks
		);
		gmb.ServerSnapshot.finishServerSnapshotBuffer(builder, root);
		return builder.sizedByteArray();
	}
}
