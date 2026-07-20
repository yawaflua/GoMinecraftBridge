package sdk

import (
	"fmt"

	flat "github.com/yawaflua/GoMinecraftBridge/sdk/internal/fbs/gmb"
)

func decodeTickSnapshot(input []byte) (snapshot ServerSnapshot, err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			err = fmt.Errorf("sdk: invalid FlatBuffers tick snapshot: %v", recovered)
		}
	}()

	if len(input) < 8 || !flat.ServerSnapshotBufferHasIdentifier(input) {
		return snapshot, fmt.Errorf("sdk: tick snapshot is not a GMBS FlatBuffer")
	}

	root := flat.GetRootAsServerSnapshot(input, 0)
	snapshot.Tick = root.Tick()
	snapshot.TimestampUnixMilli = root.TimestampUnixMilli()

	snapshot.Levels = make([]LevelSnapshot, root.LevelsLength())
	var level flat.LevelSnapshot
	for index := range snapshot.Levels {
		if !root.Levels(&level, index) {
			return ServerSnapshot{}, fmt.Errorf("sdk: missing level %d", index)
		}
		snapshot.Levels[index] = LevelSnapshot{
			Dimension:  string(level.Dimension()),
			GameTime:   level.GameTime(),
			DayTime:    level.DayTime(),
			Raining:    level.Raining(),
			Thundering: level.Thundering(),
		}
	}

	snapshot.Entities = make([]EntitySnapshot, root.EntitiesLength())
	var entity flat.EntitySnapshot
	for index := range snapshot.Entities {
		if !root.Entities(&entity, index) {
			return ServerSnapshot{}, fmt.Errorf("sdk: missing entity %d", index)
		}
		converted := EntitySnapshot{
			RuntimeID: int(entity.RuntimeId()),
			UUID:      string(entity.Uuid()),
			Type:      string(entity.Type()),
			Name:      string(entity.Name()),
			Dimension: string(entity.Dimension()),
			X:         entity.X(),
			Y:         entity.Y(),
			Z:         entity.Z(),
			Yaw:       entity.Yaw(),
			Pitch:     entity.Pitch(),
			VelocityX: entity.VelocityX(),
			VelocityY: entity.VelocityY(),
			VelocityZ: entity.VelocityZ(),
			Alive:     entity.Alive(),
			Player:    entity.Player(),
		}
		if entity.HasHealth() {
			health := entity.Health()
			maxHealth := entity.MaxHealth()
			converted.Health = &health
			converted.MaxHealth = &maxHealth
		}
		snapshot.Entities[index] = converted
	}

	snapshot.Blocks = make([]BlockSnapshot, root.BlocksLength())
	var block flat.BlockSnapshot
	var property flat.BlockProperty
	for index := range snapshot.Blocks {
		if !root.Blocks(&block, index) {
			return ServerSnapshot{}, fmt.Errorf("sdk: missing block %d", index)
		}
		properties := make(map[string]string, block.PropertiesLength())
		for propertyIndex := 0; propertyIndex < block.PropertiesLength(); propertyIndex++ {
			if block.Properties(&property, propertyIndex) {
				properties[string(property.Key())] = string(property.Value())
			}
		}
		snapshot.Blocks[index] = BlockSnapshot{
			Dimension:  string(block.Dimension()),
			X:          int(block.X()),
			Y:          int(block.Y()),
			Z:          int(block.Z()),
			Block:      string(block.Block()),
			Properties: properties,
		}
	}

	return snapshot, nil
}
