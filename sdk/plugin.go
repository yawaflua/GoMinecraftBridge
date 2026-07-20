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

// ConfigUpdateHandler runs after the SDK has updated a pointer stored in
// Metadata.ConfigSchema. It is optional for pointer-backed configurations and
// required when ConfigSchema is a schema/map rather than an updateable pointer.
type ConfigUpdateHandler interface {
	ConfigUpdated(context *Context, event ConfigUpdateEvent) error
}

type ChatHandler interface {
	Chat(context *Context, event ChatEvent) error
}

type DeathHandler interface {
	Death(context *Context, event DeathEvent) error
}

type AllowDamageHandler interface {
	AllowDamage(context *Context, event AllowDamageEvent) (bool, error)
}

type AfterDamageHandler interface {
	AfterDamage(context *Context, event AfterDamageEvent) error
}

type AllowDeathHandler interface {
	AllowDeath(context *Context, event AllowDeathEvent) (bool, error)
}

type MobConversionHandler interface {
	MobConversion(context *Context, event MobConversionEvent) error
}

type SystemCallResultHandler interface {
	SystemCallResult(context *Context, result SystemCallResult) error
}

type Deinitializer interface {
	Deinit(context *Context, event DeinitEvent) error
}
