package client

import "github.com/yawaflua/GoMinecraftBridge/sdk"

type Context struct {
	runtime *sdk.Context
}

func wrapContext(context *sdk.Context) *Context {
	return &Context{runtime: context}
}

func (context *Context) DisplayMessage(message string) string {
	return context.runtime.DisplayClientMessage(message)
}

func (context *Context) OpenBrowser(url string) string {
	return context.runtime.OpenClientBrowser(url)
}

func (context *Context) JoinSession(serverID string) string {
	return context.runtime.JoinClientSession(serverID)
}

func (context *Context) SaveConfig(config any) string {
	return context.runtime.SaveClientConfig(config)
}

func (context *Context) SetHUD(elements ...sdk.HUDElement) string {
	return context.runtime.SetHUD(elements...)
}

func (context *Context) ClearHUD() string {
	return context.runtime.ClearHUD()
}

func (context *Context) RenderHUD(element sdk.HUDElement) string {
	return context.runtime.RenderHUD(element)
}

func (context *Context) RemoveHUD(id string) string {
	return context.runtime.RemoveHUD(id)
}

func (context *Context) OpenScreen(screen sdk.ClientScreen) string {
	return context.runtime.OpenClientScreen(screen)
}

func (context *Context) CloseScreen(screenID string) string {
	return context.runtime.CloseClientScreen(screenID)
}

func (context *Context) CaptureScreen() string {
	return context.runtime.CaptureClientScreen()
}

func (context *Context) Log(level, message string) {
	context.runtime.Log(level, message)
}
