package sdk

import "encoding/json"

const ABIVersion = 3

const (
	OperationMetadata            = 1
	OperationInit                = 2
	OperationTick                = 3
	OperationChat                = 4
	OperationDeath               = 5
	OperationSystemCallResult    = 6
	OperationDeinit              = 7
	OperationClientTick          = 8
	OperationConfigUpdate        = 9
	OperationAllowDamage         = 10
	OperationAfterDamage         = 11
	OperationAllowDeath          = 12
	OperationMobConversion       = 13
	OperationClientScreenEvent   = 14
	OperationClientScreenCapture = 15
	OperationInteraction         = 16
	OperationActionResult        = 17
	OperationPlayerJoin          = 18
	OperationPlayerDisconnect    = 19
	OperationAllowChat           = 20
	OperationClientKey           = 21
	OperationClientChat          = 22
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
	ConfigSchema      any                `json:"configSchema,omitempty"`
	ConfigEnums       map[string][]any   `json:"configEnums,omitempty"`
	ConfigWritable    bool               `json:"configWritable,omitempty"`
	Environment       PluginEnvironment  `json:"environment"`
	ClientKeyBindings []ClientKeyBinding `json:"clientKeyBindings,omitempty"`
}

// ClientKeyBinding declares a configurable Minecraft key binding owned by a
// client plugin. DefaultKey uses Minecraft input names such as "key.keyboard.p".
type ClientKeyBinding struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	DefaultKey string `json:"defaultKey"`
}

type InitEvent struct {
	MinecraftVersion   string            `json:"minecraftVersion"`
	Dedicated          bool              `json:"dedicated"`
	DataDirectory      string            `json:"dataDirectory"`
	RuntimeEnvironment PluginEnvironment `json:"runtimeEnvironment"`
	Capabilities       []Capability      `json:"capabilities,omitempty"`
}

// Supports reports whether the current host declared a capability during Init.
func (event InitEvent) Supports(capability Capability) bool {
	for _, available := range event.Capabilities {
		if available == capability {
			return true
		}
	}
	return false
}

// Capability identifies an optional host event, action, or system call.
type Capability string

const (
	CapabilityActionResults         Capability = "gbm:action_results"
	CapabilityServerTick            Capability = "minecraft:event.server_tick"
	CapabilityClientTick            Capability = "minecraft:event.client_tick"
	CapabilityClientKey             Capability = "minecraft:event.client_key"
	CapabilityChatEvent             Capability = "minecraft:event.chat"
	CapabilityAllowChat             Capability = "minecraft:event.chat.allow"
	CapabilityPlayerJoinEvent       Capability = "minecraft:event.player_join"
	CapabilityPlayerDisconnectEvent Capability = "minecraft:event.player_disconnect"
	CapabilityDeathEvent            Capability = "minecraft:event.death"
	CapabilityInteractionEvent      Capability = "minecraft:event.interaction"
	CapabilityAllowDamage           Capability = "minecraft:event.damage.allow"
	CapabilityAfterDamage           Capability = "minecraft:event.damage.after"
	CapabilityAllowDeath            Capability = "minecraft:event.death.allow"
	CapabilityMobConversion         Capability = "minecraft:event.mob_conversion"
	CapabilityChatBroadcast         Capability = "minecraft:chat.broadcast"
	CapabilityChatPlayer            Capability = "minecraft:chat.player"
	CapabilityClientChat            Capability = "minecraft:client.chat.display"
	CapabilityClientHUD             Capability = "minecraft:client.hud"
	CapabilityClientScreen          Capability = "minecraft:client.screen"
	CapabilityClientScreenCapture   Capability = "minecraft:client.screen.capture"
	CapabilityClientChatEvent       Capability = "minecraft:event.client_chat"
	CapabilityClientBrowser         Capability = "minecraft:client.browser.open"
	CapabilityClientSessionJoin     Capability = "minecraft:client.session.join"
	CapabilityClientConfigSave      Capability = "minecraft:client.config.save"
)

// ClientTickEvent contains client-local state. Pointer-like values are empty
// when the client is at the title screen or is not connected to a world.
type ClientTickEvent struct {
	Tick               int64   `json:"tick"`
	TimestampUnixMilli int64   `json:"timestampUnixMilli"`
	Connected          bool    `json:"connected"`
	ServerAddress      string  `json:"serverAddress,omitempty"`
	PlayerUUID         string  `json:"playerUuid,omitempty"`
	PlayerName         string  `json:"playerName,omitempty"`
	Dimension          string  `json:"dimension,omitempty"`
	HasPosition        bool    `json:"hasPosition"`
	X                  float64 `json:"x"`
	Y                  float64 `json:"y"`
	Z                  float64 `json:"z"`
	DayTime            int64   `json:"dayTime"`
	FPS                int     `json:"fps"`
}

type ClientChatEvent struct {
	Message     string   `json:"message"`
	ClickValues []string `json:"clickValues"`
}

// ClientKeyEvent reports one press of a key binding declared in Metadata.
type ClientKeyEvent struct {
	ID                 string            `json:"id"`
	Key                string            `json:"key"`
	TimestampUnixMilli int64             `json:"timestampUnixMilli"`
	RuntimeEnvironment PluginEnvironment `json:"runtimeEnvironment,omitempty"`
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

type PlayerConnectionEvent struct {
	Player             EntitySnapshot `json:"player"`
	TimestampUnixMilli int64          `json:"timestampUnixMilli"`
}

// InteractionAction identifies an observed world click.
type InteractionAction string

const (
	InteractionUseBlock     InteractionAction = "use_block"
	InteractionAttackBlock  InteractionAction = "attack_block"
	InteractionUseEntity    InteractionAction = "use_entity"
	InteractionAttackEntity InteractionAction = "attack_entity"
)

// InteractionEvent reports a player click without changing vanilla handling.
// Block is set for block interactions and Target for entity interactions.
type InteractionEvent struct {
	Action             InteractionAction `json:"action"`
	Hand               string            `json:"hand"`
	Sneaking           bool              `json:"sneaking"`
	Sprinting          bool              `json:"sprinting"`
	Player             EntitySnapshot    `json:"player"`
	Block              *BlockSnapshot    `json:"block,omitempty"`
	Target             *EntitySnapshot   `json:"target,omitempty"`
	Face               string            `json:"face,omitempty"`
	HitX               *float64          `json:"hitX,omitempty"`
	HitY               *float64          `json:"hitY,omitempty"`
	HitZ               *float64          `json:"hitZ,omitempty"`
	TargetTexts        []string          `json:"targetTexts,omitempty"`
	TimestampUnixMilli int64             `json:"timestampUnixMilli"`
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

type ServerInfo struct {
	Tick             int64  `json:"tick"`
	Dedicated        bool   `json:"dedicated"`
	OnlinePlayers    int    `json:"onlinePlayers"`
	MaxPlayers       int    `json:"maxPlayers,omitempty"`
	MinecraftVersion string `json:"minecraftVersion,omitempty"`
	Server           string `json:"server,omitempty"`
}

type PlayerGetRequest struct {
	PlayerUUID string `json:"playerUuid"`
}

type PlayerInfo struct {
	UUID      string  `json:"uuid"`
	Name      string  `json:"name"`
	Dimension string  `json:"dimension"`
	X         float64 `json:"x"`
	Y         float64 `json:"y"`
	Z         float64 `json:"z"`
}

type BlockGetResult struct {
	Loaded bool   `json:"loaded"`
	Block  string `json:"block,omitempty"`
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
	ID      string `json:"id,omitempty"`
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

// ActionResult reports whether the host applied a queued action.
type ActionResult struct {
	ID      string `json:"id"`
	Type    string `json:"type"`
	Success bool   `json:"success"`
	Error   string `json:"error,omitempty"`
}

// ClientScreen describes a client-local Minecraft form. Text is rendered as
// literal text. IDs are plugin-local and are returned with interaction events.
type ClientScreen struct {
	ID       string                `json:"id"`
	Title    string                `json:"title"`
	Body     string                `json:"body,omitempty"`
	Elements []ClientScreenElement `json:"elements,omitempty"`
	Fields   []ClientScreenField   `json:"fields,omitempty"`
	Buttons  []ClientScreenButton  `json:"buttons,omitempty"`
}

// ClientScreenElementType selects a primitive in a freely positioned custom
// screen. Elements are painted in slice order.
type ClientScreenElementType string

const (
	ClientScreenElementText          ClientScreenElementType = "text"
	ClientScreenElementRectangle     ClientScreenElementType = "rectangle"
	ClientScreenElementButton        ClientScreenElementType = "button"
	ClientScreenElementHitbox        ClientScreenElementType = "hitbox"
	ClientScreenElementTextInput     ClientScreenElementType = "text_input"
	ClientScreenElementNumberInput   ClientScreenElementType = "number_input"
	ClientScreenElementPasswordInput ClientScreenElementType = "password_input"
	ClientScreenElementSelect        ClientScreenElementType = "select"
)

// ClientScreenElement is an arbitrarily positioned custom-screen primitive.
// Coordinates use GUI-scaled pixels relative to Anchor. A hitbox makes any
// separately drawn composition clickable without adding a vanilla button.
// Interactive elements return values under ID; Close restores the parent.
type ClientScreenElement struct {
	ID          string                  `json:"id"`
	Type        ClientScreenElementType `json:"type"`
	X           int                     `json:"x"`
	Y           int                     `json:"y"`
	Width       int                     `json:"width,omitempty"`
	Height      int                     `json:"height,omitempty"`
	Text        string                  `json:"text,omitempty"`
	Placeholder string                  `json:"placeholder,omitempty"`
	Value       string                  `json:"value,omitempty"`
	MaxLength   int                     `json:"maxLength,omitempty"`
	Options     []ClientScreenOption    `json:"options,omitempty"`
	Color       uint32                  `json:"color,omitempty"`
	Shadow      bool                    `json:"shadow,omitempty"`
	Anchor      HUDAnchor               `json:"anchor,omitempty"`
	Close       bool                    `json:"close,omitempty"`
}

// ClientScreenFieldType selects the widget used by a client screen field.
type ClientScreenFieldType string

const (
	ClientScreenFieldText     ClientScreenFieldType = "text"
	ClientScreenFieldNumber   ClientScreenFieldType = "number"
	ClientScreenFieldPassword ClientScreenFieldType = "password"
	ClientScreenFieldSelect   ClientScreenFieldType = "select"
)

// ClientScreenField is one editable value in a client screen.
type ClientScreenField struct {
	ID          string                `json:"id"`
	Type        ClientScreenFieldType `json:"type"`
	Label       string                `json:"label"`
	Placeholder string                `json:"placeholder,omitempty"`
	Value       string                `json:"value,omitempty"`
	MaxLength   int                   `json:"maxLength,omitempty"`
	Options     []ClientScreenOption  `json:"options,omitempty"`
}

// ClientScreenOption is one value offered by a select field.
type ClientScreenOption struct {
	Value string `json:"value"`
	Label string `json:"label"`
}

// ClientScreenButton describes an action button. Close restores the parent
// screen before the event is delivered, allowing the handler to open another.
type ClientScreenButton struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Close bool   `json:"close,omitempty"`
}

// ClientScreenEvent reports a button press or screen closure to a Go plugin.
type ClientScreenEvent struct {
	ScreenID string            `json:"screenId"`
	Type     string            `json:"type"`
	ButtonID string            `json:"buttonId,omitempty"`
	Reason   string            `json:"reason,omitempty"`
	Values   map[string]string `json:"values,omitempty"`
}

// ClientPixelFormat identifies the byte layout of a captured framebuffer.
type ClientPixelFormat string

const ClientPixelFormatRGBA8 ClientPixelFormat = "rgba8"

// ClientScreenCapture contains one full framebuffer. Pixels contains tightly
// packed top-to-bottom RGBA8 rows and is valid for the duration of the callback.
type ClientScreenCapture struct {
	Width  int
	Height int
	Stride int
	Format ClientPixelFormat
	Pixels []byte
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
