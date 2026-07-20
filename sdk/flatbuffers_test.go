package sdk

import (
	"testing"

	flatbuffers "github.com/google/flatbuffers/go"
	flat "github.com/yawaflua/GoMinecraftBridge/sdk/internal/fbs/gmb"
)

func TestDecodeTickSnapshot(t *testing.T) {
	want := benchmarkSnapshot(3)
	encoded := encodeBenchmarkFlatBuffer(want)
	got, err := decodeTickSnapshot(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if got.Tick != want.Tick || len(got.Entities) != 3 || got.Entities[2].UUID != want.Entities[2].UUID {
		t.Fatalf("unexpected decoded snapshot: %#v", got)
	}
}

func BenchmarkFlatBuffersSnapshot1000Entities(b *testing.B) {
	encoded := encodeBenchmarkFlatBuffer(benchmarkSnapshot(1000))
	b.ReportAllocs()
	for b.Loop() {
		if _, err := decodeTickSnapshot(encoded); err != nil {
			b.Fatal(err)
		}
	}
	b.ReportMetric(float64(len(encoded)), "bytes/snapshot")
}

func encodeBenchmarkFlatBuffer(snapshot ServerSnapshot) []byte {
	builder := flatbuffers.NewBuilder(16 * 1024)
	levels := make([]flatbuffers.UOffsetT, len(snapshot.Levels))
	for index, level := range snapshot.Levels {
		dimension := builder.CreateString(level.Dimension)
		flat.LevelSnapshotStart(builder)
		flat.LevelSnapshotAddDimension(builder, dimension)
		flat.LevelSnapshotAddGameTime(builder, level.GameTime)
		flat.LevelSnapshotAddDayTime(builder, level.DayTime)
		flat.LevelSnapshotAddRaining(builder, level.Raining)
		flat.LevelSnapshotAddThundering(builder, level.Thundering)
		levels[index] = flat.LevelSnapshotEnd(builder)
	}

	entities := make([]flatbuffers.UOffsetT, len(snapshot.Entities))
	for index, entity := range snapshot.Entities {
		uuid := builder.CreateString(entity.UUID)
		entityType := builder.CreateString(entity.Type)
		name := builder.CreateString(entity.Name)
		dimension := builder.CreateString(entity.Dimension)
		hasHealth := entity.Health != nil && entity.MaxHealth != nil
		var health, maxHealth float32
		if hasHealth {
			health = *entity.Health
			maxHealth = *entity.MaxHealth
		}
		flat.EntitySnapshotStart(builder)
		flat.EntitySnapshotAddRuntimeId(builder, int32(entity.RuntimeID))
		flat.EntitySnapshotAddUuid(builder, uuid)
		flat.EntitySnapshotAddType(builder, entityType)
		flat.EntitySnapshotAddName(builder, name)
		flat.EntitySnapshotAddDimension(builder, dimension)
		flat.EntitySnapshotAddX(builder, entity.X)
		flat.EntitySnapshotAddY(builder, entity.Y)
		flat.EntitySnapshotAddZ(builder, entity.Z)
		flat.EntitySnapshotAddYaw(builder, entity.Yaw)
		flat.EntitySnapshotAddPitch(builder, entity.Pitch)
		flat.EntitySnapshotAddVelocityX(builder, entity.VelocityX)
		flat.EntitySnapshotAddVelocityY(builder, entity.VelocityY)
		flat.EntitySnapshotAddVelocityZ(builder, entity.VelocityZ)
		flat.EntitySnapshotAddAlive(builder, entity.Alive)
		flat.EntitySnapshotAddPlayer(builder, entity.Player)
		flat.EntitySnapshotAddHasHealth(builder, hasHealth)
		flat.EntitySnapshotAddHealth(builder, health)
		flat.EntitySnapshotAddMaxHealth(builder, maxHealth)
		entities[index] = flat.EntitySnapshotEnd(builder)
	}

	flat.ServerSnapshotStartLevelsVector(builder, len(levels))
	for index := len(levels) - 1; index >= 0; index-- {
		builder.PrependUOffsetT(levels[index])
	}
	levelVector := builder.EndVector(len(levels))
	flat.ServerSnapshotStartEntitiesVector(builder, len(entities))
	for index := len(entities) - 1; index >= 0; index-- {
		builder.PrependUOffsetT(entities[index])
	}
	entityVector := builder.EndVector(len(entities))
	flat.ServerSnapshotStartBlocksVector(builder, 0)
	blockVector := builder.EndVector(0)

	flat.ServerSnapshotStart(builder)
	flat.ServerSnapshotAddTick(builder, snapshot.Tick)
	flat.ServerSnapshotAddTimestampUnixMilli(builder, snapshot.TimestampUnixMilli)
	flat.ServerSnapshotAddLevels(builder, levelVector)
	flat.ServerSnapshotAddEntities(builder, entityVector)
	flat.ServerSnapshotAddBlocks(builder, blockVector)
	root := flat.ServerSnapshotEnd(builder)
	flat.FinishServerSnapshotBuffer(builder, root)
	return builder.FinishedBytes()
}
