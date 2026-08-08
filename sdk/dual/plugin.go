package dual

import (
	"github.com/yawaflua/GoMinecraftBridge/sdk"
	"github.com/yawaflua/GoMinecraftBridge/sdk/client"
	"github.com/yawaflua/GoMinecraftBridge/sdk/server"
)

func Register(metadata sdk.Metadata, serverPlugin, clientPlugin any) {
	if metadata.Environment != "" && metadata.Environment != sdk.PluginEnvironmentBoth {
		panic("dual: metadata environment must be both or empty")
	}
	metadata.Environment = sdk.PluginEnvironmentBoth
	sdk.RegisterRuntime(&adapter{
		metadata: metadata,
		server:   server.Adapt(serverMetadata(metadata), serverPlugin),
		client:   client.Adapt(clientMetadata(metadata), clientPlugin),
	})
}

type adapter struct {
	metadata sdk.Metadata
	server   sdk.Plugin
	client   sdk.Plugin
}

func serverMetadata(metadata sdk.Metadata) sdk.Metadata {
	metadata.Environment = sdk.PluginEnvironmentServer
	return metadata
}

func clientMetadata(metadata sdk.Metadata) sdk.Metadata {
	metadata.Environment = sdk.PluginEnvironmentClient
	return metadata
}

func (adapter *adapter) Metadata() sdk.Metadata {
	return adapter.metadata
}

func (adapter *adapter) HandlesConfigUpdates(environment sdk.PluginEnvironment) bool {
	return handlesConfigUpdates(adapter.sideFor(environment), environment)
}

func handlesConfigUpdates(plugin sdk.Plugin, environment sdk.PluginEnvironment) bool {
	support, ok := plugin.(sdk.ConfigUpdateSupport)
	return ok && support.HandlesConfigUpdates(environment)
}

func (adapter *adapter) Init(context *sdk.Context, event sdk.InitEvent) error {
	plugin := adapter.server
	if event.RuntimeEnvironment == sdk.PluginEnvironmentClient {
		plugin = adapter.client
	}
	if handler, ok := plugin.(sdk.Initializer); ok {
		return handler.Init(context, event)
	}
	return nil
}

func (adapter *adapter) Tick(context *sdk.Context, snapshot sdk.ServerSnapshot) error {
	if handler, ok := adapter.server.(sdk.TickHandler); ok {
		return handler.Tick(context, snapshot)
	}
	return nil
}

func (adapter *adapter) ClientTick(context *sdk.Context, event sdk.ClientTickEvent) error {
	if handler, ok := adapter.client.(sdk.ClientTickHandler); ok {
		return handler.ClientTick(context, event)
	}
	return nil
}

func (adapter *adapter) ClientKey(context *sdk.Context, event sdk.ClientKeyEvent) error {
	if handler, ok := adapter.client.(sdk.ClientKeyHandler); ok {
		return handler.ClientKey(context, event)
	}
	return nil
}

func (adapter *adapter) ConfigUpdated(context *sdk.Context, event sdk.ConfigUpdateEvent) error {
	if handler, ok := adapter.side(context).(sdk.ConfigUpdateHandler); ok {
		return handler.ConfigUpdated(context, event)
	}
	return nil
}

func (adapter *adapter) Chat(context *sdk.Context, event sdk.ChatEvent) error {
	if handler, ok := adapter.server.(sdk.ChatHandler); ok {
		return handler.Chat(context, event)
	}
	return nil
}

func (adapter *adapter) AllowChat(context *sdk.Context, event sdk.ChatEvent) (bool, error) {
	if handler, ok := adapter.server.(sdk.AllowChatHandler); ok {
		return handler.AllowChat(context, event)
	}
	return true, nil
}

func (adapter *adapter) PlayerJoin(context *sdk.Context, event sdk.PlayerConnectionEvent) error {
	if handler, ok := adapter.server.(sdk.PlayerJoinHandler); ok {
		return handler.PlayerJoin(context, event)
	}
	return nil
}

func (adapter *adapter) PlayerDisconnect(context *sdk.Context, event sdk.PlayerConnectionEvent) error {
	if handler, ok := adapter.server.(sdk.PlayerDisconnectHandler); ok {
		return handler.PlayerDisconnect(context, event)
	}
	return nil
}

func (adapter *adapter) Interaction(context *sdk.Context, event sdk.InteractionEvent) error {
	if handler, ok := adapter.side(context).(sdk.InteractionHandler); ok {
		return handler.Interaction(context, event)
	}
	return nil
}

func (adapter *adapter) Death(context *sdk.Context, event sdk.DeathEvent) error {
	if handler, ok := adapter.server.(sdk.DeathHandler); ok {
		return handler.Death(context, event)
	}
	return nil
}

func (adapter *adapter) AllowDamage(context *sdk.Context, event sdk.AllowDamageEvent) (bool, error) {
	if handler, ok := adapter.server.(sdk.AllowDamageHandler); ok {
		return handler.AllowDamage(context, event)
	}
	return true, nil
}

func (adapter *adapter) AfterDamage(context *sdk.Context, event sdk.AfterDamageEvent) error {
	if handler, ok := adapter.server.(sdk.AfterDamageHandler); ok {
		return handler.AfterDamage(context, event)
	}
	return nil
}

func (adapter *adapter) AllowDeath(context *sdk.Context, event sdk.AllowDeathEvent) (bool, error) {
	if handler, ok := adapter.server.(sdk.AllowDeathHandler); ok {
		return handler.AllowDeath(context, event)
	}
	return true, nil
}

func (adapter *adapter) MobConversion(context *sdk.Context, event sdk.MobConversionEvent) error {
	if handler, ok := adapter.server.(sdk.MobConversionHandler); ok {
		return handler.MobConversion(context, event)
	}
	return nil
}

func (adapter *adapter) ClientScreenEvent(context *sdk.Context, event sdk.ClientScreenEvent) error {
	if handler, ok := adapter.client.(sdk.ClientScreenEventHandler); ok {
		return handler.ClientScreenEvent(context, event)
	}
	return nil
}

func (adapter *adapter) ClientScreenCaptured(context *sdk.Context, capture sdk.ClientScreenCapture) error {
	if handler, ok := adapter.client.(sdk.ClientScreenCaptureHandler); ok {
		return handler.ClientScreenCaptured(context, capture)
	}
	return nil
}

func (adapter *adapter) SystemCallResult(context *sdk.Context, result sdk.SystemCallResult) error {
	if handler, ok := adapter.side(context).(sdk.SystemCallResultHandler); ok {
		return handler.SystemCallResult(context, result)
	}
	return nil
}

func (adapter *adapter) ActionResult(context *sdk.Context, result sdk.ActionResult) error {
	if handler, ok := adapter.side(context).(sdk.ActionResultHandler); ok {
		return handler.ActionResult(context, result)
	}
	return nil
}

func (adapter *adapter) Deinit(context *sdk.Context, event sdk.DeinitEvent) error {
	if handler, ok := adapter.side(context).(sdk.Deinitializer); ok {
		return handler.Deinit(context, event)
	}
	return nil
}

func (adapter *adapter) side(context *sdk.Context) sdk.Plugin {
	return adapter.sideFor(context.RuntimeEnvironment())
}

func (adapter *adapter) sideFor(environment sdk.PluginEnvironment) sdk.Plugin {
	if environment == sdk.PluginEnvironmentClient {
		return adapter.client
	}
	return adapter.server
}
