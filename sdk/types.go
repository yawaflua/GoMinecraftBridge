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
	OperationConfigUpdate     = 9
	OperationAllowDamage      = 10
	OperationAfterDamage      = 11
	OperationAllowDeath       = 12
	OperationMobConversion    = 13
)

// PluginEnvironment declares which Minecraft process may execute a plugin.
type PluginEnvironment string

const (
	PluginEnvironmentServer PluginEnvironment = "server"
	PluginEnvironmentClient PluginEnvironment = "client"
	PluginEnvironmentBoth   PluginEnvironment = "both"
)

type Metadata struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Version     string   `json:"version"`
	Description string   `json:"description,omitempty"`
	Authors     []string `json:"authors,omitempty"`
	Website     string   `json:"website,omitempty"`
	License     string   `json:"license,omitempty"`
	APIVersion  int      `json:"apiVersion"`
	// ConfigSchema exposes the plugin's editable configuration to the client.
	// A pointer to a JSON-serializable struct is updated in place when the user
	// saves its Cloth Config screen. Maps containing a JSON Schema remain
	// supported when the plugin implements ConfigUpdateHandler itself.
	ConfigSchema   any               `json:"configSchema,omitempty"`
	ConfigWritable bool              `json:"configWritable,omitempty"`
	Environment    PluginEnvironment `json:"environment"`
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

// AllowDamageEvent is emitted before damage is applied. Returning false from
// AllowDamageHandler prevents this damage instance.
type AllowDamageEvent struct {
	Entity             EntitySnapshot `json:"entity"`
	DamageType         string         `json:"damageType"`
	AttackerUUID       *string        `json:"attackerUuid"`
	Amount             float32        `json:"amount"`
	TimestampUnixMilli int64          `json:"timestampUnixMilli"`
}

// AfterDamageEvent contains both vanilla's calculated base damage and the
// amount that was actually applied after armor, effects, and blocking.
type AfterDamageEvent struct {
	Entity             EntitySnapshot `json:"entity"`
	DamageType         string         `json:"damageType"`
	AttackerUUID       *string        `json:"attackerUuid"`
	BaseDamageTaken    float32        `json:"baseDamageTaken"`
	DamageTaken        float32        `json:"damageTaken"`
	Blocked            bool           `json:"blocked"`
	TimestampUnixMilli int64          `json:"timestampUnixMilli"`
}

// AllowDeathEvent is emitted after lethal damage is established but before
// the entity dies. Returning false from AllowDeathHandler prevents the death.
type AllowDeathEvent struct {
	Entity             EntitySnapshot `json:"entity"`
	DamageType         string         `json:"damageType"`
	AttackerUUID       *string        `json:"attackerUuid"`
	DamageAmount       float32        `json:"damageAmount"`
	TimestampUnixMilli int64          `json:"timestampUnixMilli"`
}

type MobConversionEvent struct {
	Previous           EntitySnapshot `json:"previous"`
	Converted          EntitySnapshot `json:"converted"`
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
	SystemCallKillEntity SystemCallType = "minecraft:entity.kill"
	SystemCallPlayerGet  SystemCallType = "minecraft:player.get"
	SystemCallBlockGet   SystemCallType = "minecraft:block.get"
	SystemCallGetEntity  SystemCallType = "minecraft:entity.get"
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

// ConfigUpdateEvent contains the complete configuration saved by the user.
// Config is decoded into Metadata.ConfigSchema automatically when that value
// is a non-nil pointer. Handlers can inspect the raw JSON for custom schemas.
type ConfigUpdateEvent struct {
	Config json.RawMessage `json:"config"`
}

type ActionRequest struct {
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

// HUDAnchor controls which screen point the element's X/Y offset is relative to.
type HUDAnchor string

const (
	HUDTopLeft      HUDAnchor = "top_left"
	HUDTopCenter    HUDAnchor = "top_center"
	HUDTopRight     HUDAnchor = "top_right"
	HUDCenterLeft   HUDAnchor = "center_left"
	HUDCenter       HUDAnchor = "center"
	HUDCenterRight  HUDAnchor = "center_right"
	HUDBottomLeft   HUDAnchor = "bottom_left"
	HUDBottomCenter HUDAnchor = "bottom_center"
	HUDBottomRight  HUDAnchor = "bottom_right"
)

// HUDElement is a retained client HUD primitive. Color uses ARGB byte order.
type HUDElement struct {
	ID     string    `json:"id,omitempty"`
	Type   string    `json:"type"`
	X      int       `json:"x"`
	Y      int       `json:"y"`
	Width  int       `json:"width,omitempty"`
	Height int       `json:"height,omitempty"`
	Text   string    `json:"text,omitempty"`
	Color  uint32    `json:"color"`
	Shadow bool      `json:"shadow,omitempty"`
	Anchor HUDAnchor `json:"anchor,omitempty"`
	// DurationMillis removes the element automatically after this many
	// milliseconds. Zero keeps it until explicitly replaced or removed.
	DurationMillis int64 `json:"durationMillis,omitempty"`
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
