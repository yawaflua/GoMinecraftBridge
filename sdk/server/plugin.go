package server

import "github.com/yawaflua/GoMinecraftBridge/sdk"

type Initializer interface {
	Init(context *Context, event sdk.InitEvent) error
}

type TickHandler interface {
	Tick(context *Context, snapshot sdk.ServerSnapshot) error
}

type ConfigUpdateHandler interface {
	ConfigUpdated(context *Context, event sdk.ConfigUpdateEvent) error
}

type ChatHandler interface {
	Chat(context *Context, event sdk.ChatEvent) error
}

type AllowChatHandler interface {
	AllowChat(context *Context, event sdk.ChatEvent) (bool, error)
}

type PlayerJoinHandler interface {
	PlayerJoin(context *Context, event sdk.PlayerConnectionEvent) error
}

type PlayerDisconnectHandler interface {
	PlayerDisconnect(context *Context, event sdk.PlayerConnectionEvent) error
}

type InteractionHandler interface {
	Interaction(context *Context, event sdk.InteractionEvent) error
}

type DeathHandler interface {
	Death(context *Context, event sdk.DeathEvent) error
}

type AllowDamageHandler interface {
	AllowDamage(context *Context, event sdk.AllowDamageEvent) (bool, error)
}

type AfterDamageHandler interface {
	AfterDamage(context *Context, event sdk.AfterDamageEvent) error
}

type AllowDeathHandler interface {
	AllowDeath(context *Context, event sdk.AllowDeathEvent) (bool, error)
}

type MobConversionHandler interface {
	MobConversion(context *Context, event sdk.MobConversionEvent) error
}

type SystemCallResultHandler interface {
	SystemCallResult(context *Context, result sdk.SystemCallResult) error
}

type ActionResultHandler interface {
	ActionResult(context *Context, result sdk.ActionResult) error
}

type Deinitializer interface {
	Deinit(context *Context, event sdk.DeinitEvent) error
}

func Register(metadata sdk.Metadata, plugin any) {
	sdk.RegisterRuntime(Adapt(metadata, plugin))
}

func Adapt(metadata sdk.Metadata, plugin any) sdk.Plugin {
	if plugin == nil {
		panic("server: cannot register a nil plugin")
	}
	if metadata.Environment != "" && metadata.Environment != sdk.PluginEnvironmentServer {
		panic("server: metadata environment must be server or empty")
	}
	metadata.Environment = sdk.PluginEnvironmentServer
	return &adapter{metadata: metadata, plugin: plugin}
}

type adapter struct {
	metadata sdk.Metadata
	plugin   any
}

func (adapter *adapter) Metadata() sdk.Metadata {
	return adapter.metadata
}

func (adapter *adapter) HandlesConfigUpdates(environment sdk.PluginEnvironment) bool {
	_, ok := adapter.plugin.(ConfigUpdateHandler)
	return environment == sdk.PluginEnvironmentServer && ok
}

func (adapter *adapter) Init(context *sdk.Context, event sdk.InitEvent) error {
	if handler, ok := adapter.plugin.(Initializer); ok {
		return handler.Init(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) Tick(context *sdk.Context, snapshot sdk.ServerSnapshot) error {
	if handler, ok := adapter.plugin.(TickHandler); ok {
		return handler.Tick(wrapContext(context), snapshot)
	}
	return nil
}

func (adapter *adapter) ConfigUpdated(context *sdk.Context, event sdk.ConfigUpdateEvent) error {
	if handler, ok := adapter.plugin.(ConfigUpdateHandler); ok {
		return handler.ConfigUpdated(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) Chat(context *sdk.Context, event sdk.ChatEvent) error {
	if handler, ok := adapter.plugin.(ChatHandler); ok {
		return handler.Chat(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) AllowChat(context *sdk.Context, event sdk.ChatEvent) (bool, error) {
	if handler, ok := adapter.plugin.(AllowChatHandler); ok {
		return handler.AllowChat(wrapContext(context), event)
	}
	return true, nil
}

func (adapter *adapter) PlayerJoin(context *sdk.Context, event sdk.PlayerConnectionEvent) error {
	if handler, ok := adapter.plugin.(PlayerJoinHandler); ok {
		return handler.PlayerJoin(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) PlayerDisconnect(context *sdk.Context, event sdk.PlayerConnectionEvent) error {
	if handler, ok := adapter.plugin.(PlayerDisconnectHandler); ok {
		return handler.PlayerDisconnect(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) Interaction(context *sdk.Context, event sdk.InteractionEvent) error {
	if handler, ok := adapter.plugin.(InteractionHandler); ok {
		return handler.Interaction(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) Death(context *sdk.Context, event sdk.DeathEvent) error {
	if handler, ok := adapter.plugin.(DeathHandler); ok {
		return handler.Death(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) AllowDamage(context *sdk.Context, event sdk.AllowDamageEvent) (bool, error) {
	if handler, ok := adapter.plugin.(AllowDamageHandler); ok {
		return handler.AllowDamage(wrapContext(context), event)
	}
	return true, nil
}

func (adapter *adapter) AfterDamage(context *sdk.Context, event sdk.AfterDamageEvent) error {
	if handler, ok := adapter.plugin.(AfterDamageHandler); ok {
		return handler.AfterDamage(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) AllowDeath(context *sdk.Context, event sdk.AllowDeathEvent) (bool, error) {
	if handler, ok := adapter.plugin.(AllowDeathHandler); ok {
		return handler.AllowDeath(wrapContext(context), event)
	}
	return true, nil
}

func (adapter *adapter) MobConversion(context *sdk.Context, event sdk.MobConversionEvent) error {
	if handler, ok := adapter.plugin.(MobConversionHandler); ok {
		return handler.MobConversion(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) SystemCallResult(context *sdk.Context, result sdk.SystemCallResult) error {
	if handler, ok := adapter.plugin.(SystemCallResultHandler); ok {
		return handler.SystemCallResult(wrapContext(context), result)
	}
	return nil
}

func (adapter *adapter) ActionResult(context *sdk.Context, result sdk.ActionResult) error {
	if handler, ok := adapter.plugin.(ActionResultHandler); ok {
		return handler.ActionResult(wrapContext(context), result)
	}
	return nil
}

func (adapter *adapter) Deinit(context *sdk.Context, event sdk.DeinitEvent) error {
	if handler, ok := adapter.plugin.(Deinitializer); ok {
		return handler.Deinit(wrapContext(context), event)
	}
	return nil
}
