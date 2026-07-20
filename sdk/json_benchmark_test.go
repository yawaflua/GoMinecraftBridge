package sdk

import (
	"encoding/json"
	"fmt"
	"testing"
)

func BenchmarkJSONSnapshot1000Entities(b *testing.B) {
	snapshot := benchmarkSnapshot(1000)
	encoded, err := json.Marshal(snapshot)
	if err != nil {
		b.Fatal(err)
	}
	b.Run("encode", func(b *testing.B) {
		b.ReportAllocs()
		for b.Loop() {
			if _, err := json.Marshal(snapshot); err != nil {
				b.Fatal(err)
			}
		}
		b.ReportMetric(float64(len(encoded)), "bytes/snapshot")
	})

	b.Run("decode", func(b *testing.B) {
		b.ReportAllocs()
		for b.Loop() {
			var decoded ServerSnapshot
			if err := json.Unmarshal(encoded, &decoded); err != nil {
				b.Fatal(err)
			}
		}
		b.ReportMetric(float64(len(encoded)), "bytes/snapshot")
	})
}

func benchmarkSnapshot(entityCount int) ServerSnapshot {
	entities := make([]EntitySnapshot, entityCount)
	for index := range entities {
		health := float32(20)
		entities[index] = EntitySnapshot{
			RuntimeID: index,
			UUID:      fmt.Sprintf("00000000-0000-0000-0000-%012d", index),
			Type:      "minecraft:zombie",
			Name:      "Zombie",
			Dimension: "minecraft:overworld",
			X:         float64(index),
			Y:         64,
			Z:         float64(-index),
			Alive:     true,
			Health:    &health,
			MaxHealth: &health,
		}
	}
	return ServerSnapshot{
		Tick:               1,
		TimestampUnixMilli: 1,
		Levels: []LevelSnapshot{{
			Dimension: "minecraft:overworld",
		}},
		Entities: entities,
	}
}
