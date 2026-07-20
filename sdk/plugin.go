package sdk

type Plugin interface {
	Metadata() Metadata
}

type Initializer interface {
	Init(context *Context, event InitEvent) error
}

type TickHandler interface {
	Tick(context *Context, snapshot ServerSnapshot) error
}

// ClientTickHandler is invoked only by a client native runtime. A plugin with
// environment "both" may implement TickHandler, ClientTickHandler, or both.
type ClientTickHandler interface {
	ClientTick(context *Context, event ClientTickEvent) error
}

type ChatHandler interface {
	Chat(context *Context, event ChatEvent) error
}

type DeathHandler interface {
	Death(context *Context, event DeathEvent) error
}

type SystemCallResultHandler interface {
	SystemCallResult(context *Context, result SystemCallResult) error
}

type Deinitializer interface {
	Deinit(context *Context, event DeinitEvent) error
}
