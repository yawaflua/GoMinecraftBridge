package client

import "github.com/yawaflua/GoMinecraftBridge/sdk"

type Initializer interface {
	Init(context *Context, event sdk.InitEvent) error
}

type TickHandler interface {
	Tick(context *Context, event sdk.ClientTickEvent) error
}

type KeyHandler interface {
	KeyPressed(context *Context, event sdk.ClientKeyEvent) error
}

type ChatHandler interface {
	ChatReceived(context *Context, event sdk.ClientChatEvent) error
}

type ConfigUpdateHandler interface {
	ConfigUpdated(context *Context, event sdk.ConfigUpdateEvent) error
}

type ScreenEventHandler interface {
	ScreenEvent(context *Context, event sdk.ClientScreenEvent) error
}

type ScreenCaptureHandler interface {
	ScreenCaptured(context *Context, capture sdk.ClientScreenCapture) error
}

type InteractionHandler interface {
	Interaction(context *Context, event sdk.InteractionEvent) error
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
		panic("client: cannot register a nil plugin")
	}
	if metadata.Environment != "" && metadata.Environment != sdk.PluginEnvironmentClient {
		panic("client: metadata environment must be client or empty")
	}
	metadata.Environment = sdk.PluginEnvironmentClient
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
	return environment == sdk.PluginEnvironmentClient && ok
}

func (adapter *adapter) Init(context *sdk.Context, event sdk.InitEvent) error {
	if handler, ok := adapter.plugin.(Initializer); ok {
		return handler.Init(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ClientTick(context *sdk.Context, event sdk.ClientTickEvent) error {
	if handler, ok := adapter.plugin.(TickHandler); ok {
		return handler.Tick(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ClientKey(context *sdk.Context, event sdk.ClientKeyEvent) error {
	if handler, ok := adapter.plugin.(KeyHandler); ok {
		return handler.KeyPressed(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ClientChat(context *sdk.Context, event sdk.ClientChatEvent) error {
	if handler, ok := adapter.plugin.(ChatHandler); ok {
		return handler.ChatReceived(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ConfigUpdated(context *sdk.Context, event sdk.ConfigUpdateEvent) error {
	if handler, ok := adapter.plugin.(ConfigUpdateHandler); ok {
		return handler.ConfigUpdated(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ClientScreenEvent(context *sdk.Context, event sdk.ClientScreenEvent) error {
	if handler, ok := adapter.plugin.(ScreenEventHandler); ok {
		return handler.ScreenEvent(wrapContext(context), event)
	}
	return nil
}

func (adapter *adapter) ClientScreenCaptured(context *sdk.Context, capture sdk.ClientScreenCapture) error {
	if handler, ok := adapter.plugin.(ScreenCaptureHandler); ok {
		return handler.ScreenCaptured(wrapContext(context), capture)
	}
	return nil
}

func (adapter *adapter) Interaction(context *sdk.Context, event sdk.InteractionEvent) error {
	if handler, ok := adapter.plugin.(InteractionHandler); ok {
		return handler.Interaction(wrapContext(context), event)
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
