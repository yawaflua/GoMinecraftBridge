package server

import "github.com/yawaflua/GoMinecraftBridge/sdk"

type Context struct {
	runtime *sdk.Context
}

func wrapContext(context *sdk.Context) *Context {
	return &Context{runtime: context}
}

func (context *Context) Broadcast(message string) string {
	return context.runtime.Broadcast(message)
}

func (context *Context) SendMessage(playerUUID, message string) string {
	return context.runtime.SendMessage(playerUUID, message)
}

func (context *Context) SystemCall(callType sdk.SystemCallType, payload any) string {
	return context.runtime.SystemCall(callType, payload)
}

func (context *Context) CustomSystemCall(name string, payload any) string {
	return context.runtime.CustomSystemCall(name, payload)
}

func (context *Context) GetServerInfo() string {
	return context.runtime.GetServerInfo()
}

func (context *Context) GetPlayer(playerUUID string) string {
	return context.runtime.GetPlayer(playerUUID)
}

func (context *Context) GetBlock(block sdk.BlockReference) string {
	return context.runtime.GetBlock(block)
}

func (context *Context) GetEntity(entity sdk.GetEntityRequest) string {
	return context.runtime.GetEntity(entity)
}

func (context *Context) Kill(entityUUID string) string {
	return context.runtime.Kill(entityUUID)
}

func (context *Context) SubscribeSnapshot(entities bool, blocks ...sdk.BlockReference) {
	context.runtime.SubscribeSnapshot(entities, blocks...)
}

func (context *Context) Log(level, message string) {
	context.runtime.Log(level, message)
}
