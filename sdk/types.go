package sdk

import "encoding/json"

const ABIVersion = 2

const (
	OperationMetadata         = 1
	OperationInit             = 2
	OperationTick             = 3
	OperationChat             = 4
	OperationDeath            = 5
	OperationSystemCallResult = 6
	OperationDeinit           = 7
	OperationClientTick       = 8
)

// PluginEnvironment declares which Minecraft process may execute a plugin.
type PluginEnvironment string

const (
	PluginEnvironmentServer PluginEnvironment = "server"
	PluginEnvironmentClient PluginEnvironment = "client"
	PluginEnvironmentBoth   PluginEnvironment = "both"
)

type Metadata struct {
	ID           string            `json:"id"`
	Name         string            `json:"name"`
	Version      string            `json:"version"`
	Description  string            `json:"description,omitempty"`
	Authors      []string          `json:"authors,omitempty"`
	Website      string            `json:"website,omitempty"`
	APIVersion   int               `json:"apiVersion"`
	ConfigSchema map[string]any    `json:"configSchema,omitempty"`
	Environment  PluginEnvironment `json:"environment"`
}

type InitEvent struct {
	MinecraftVersion   string            `json:"minecraftVersion"`
	Dedicated          bool              `json:"dedicated"`
	DataDirectory      string            `json:"dataDirectory"`
	RuntimeEnvironment PluginEnvironment `json:"runtimeEnvironment"`
}

// ClientTickEvent contains client-local state. Pointer-like values are empty
// when the client is at the title screen or is not connected to a world.
type ClientTickEvent struct {
	Tick               int64  `json:"tick"`
	TimestampUnixMilli int64  `json:"timestampUnixMilli"`
	Connected          bool   `json:"connected"`
	ServerAddress      string `json:"serverAddress,omitempty"`
	PlayerUUID         string `json:"playerUuid,omitempty"`
	PlayerName         string `json:"playerName,omitempty"`
	Dimension          string `json:"dimension,omitempty"`
}

type ServerSnapshot struct {
	Tick               int64            `json:"tick"`
	TimestampUnixMilli int64            `json:"timestampUnixMilli"`
	Levels             []LevelSnapshot  `json:"levels"`
	Entities           []EntitySnapshot `json:"entities"`
	Blocks             []BlockSnapshot  `json:"blocks"`
}

type LevelSnapshot struct {
	Dimension  string `json:"dimension"`
	GameTime   int64  `json:"gameTime"`
	DayTime    int64  `json:"dayTime"`
	Raining    bool   `json:"raining"`
	Thundering bool   `json:"thundering"`
}

type EntitySnapshot struct {
	RuntimeID int      `json:"runtimeId"`
	UUID      string   `json:"uuid"`
	Type      string   `json:"type"`
	Name      string   `json:"name"`
	Dimension string   `json:"dimension"`
	X         float64  `json:"x"`
	Y         float64  `json:"y"`
	Z         float64  `json:"z"`
	Yaw       float32  `json:"yaw"`
	Pitch     float32  `json:"pitch"`
	VelocityX float64  `json:"velocityX"`
	VelocityY float64  `json:"velocityY"`
	VelocityZ float64  `json:"velocityZ"`
	Alive     bool     `json:"alive"`
	Player    bool     `json:"player"`
	Health    *float32 `json:"health"`
	MaxHealth *float32 `json:"maxHealth"`
}

type BlockSnapshot struct {
	Dimension  string            `json:"dimension"`
	X          int               `json:"x"`
	Y          int               `json:"y"`
	Z          int               `json:"z"`
	Block      string            `json:"block"`
	Properties map[string]string `json:"properties"`
}

type BlockReference struct {
	Dimension string `json:"dimension"`
	X         int    `json:"x"`
	Y         int    `json:"y"`
	Z         int    `json:"z"`
}

type ChatEvent struct {
	PlayerUUID         string `json:"playerUuid"`
	PlayerName         string `json:"playerName"`
	Message            string `json:"message"`
	TimestampUnixMilli int64  `json:"timestampUnixMilli"`
}

type DeathEvent struct {
	Entity             EntitySnapshot `json:"entity"`
	DamageType         string         `json:"damageType"`
	AttackerUUID       *string        `json:"attackerUuid"`
	TimestampUnixMilli int64          `json:"timestampUnixMilli"`
}

type SystemCallResult struct {
	ID      string          `json:"id"`
	Name    string          `json:"name"`
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
	Error   string          `json:"error"`
}

// SystemCallType identifies a system call provided by the bridge itself.
// Use Context.CustomSystemCall for calls registered by another mod.
type SystemCallType string

const (
	SystemCallServerInfo SystemCallType = "minecraft:server.info"
	SystemCallPlayerGet  SystemCallType = "minecraft:player.get"
	SystemCallBlockGet   SystemCallType = "minecraft:block.get"
	SystemCallGetEntity  SystemCallType = "minecraft:get_entity"
)

// GetEntityRequest selects an entity by UUID or by its runtime ID.
// Exactly one field must be set.
type GetEntityRequest struct {
	UUID      string `json:"uuid,omitempty"`
	RuntimeID *int   `json:"runtimeId,omitempty"`
}

type DeinitEvent struct {
	Reason string `json:"reason"`
}

type ActionRequest struct {
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

type SystemCallRequest struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Payload any    `json:"payload"`
}

type SnapshotSubscription struct {
	Entities bool             `json:"entities"`
	Blocks   []BlockReference `json:"blocks"`
}

type LogEntry struct {
	Stream             string `json:"stream"`
	Level              string `json:"level"`
	Message            string `json:"message"`
	TimestampUnixMilli int64  `json:"timestampUnixMilli"`
}

type response struct {
	Status      string                `json:"status"`
	Error       string                `json:"error,omitempty"`
	Stack       string                `json:"stack,omitempty"`
	Data        any                   `json:"data,omitempty"`
	Logs        []LogEntry            `json:"logs,omitempty"`
	Actions     []ActionRequest       `json:"actions,omitempty"`
	SystemCalls []SystemCallRequest   `json:"systemCalls,omitempty"`
	Snapshot    *SnapshotSubscription `json:"snapshot,omitempty"`
}
