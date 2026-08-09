package sdk

import (
	"fmt"
	"sync/atomic"
	"time"
)

var callSequence atomic.Uint64

type Context struct {
	actions     []ActionRequest
	systemCalls []SystemCallRequest
	logs        []LogEntry
	snapshot    *SnapshotSubscription
	client      bool
}

// RuntimeEnvironment reports which host is executing the current callback.
func (context *Context) RuntimeEnvironment() PluginEnvironment {
	if context.client {
		return PluginEnvironmentClient
	}
	return PluginEnvironmentServer
}

// Broadcast queues a message for all players and returns its action ID.
func (context *Context) Broadcast(message string) string {
	if context.client {
		return ""
	}
	return context.queueAction("minecraft:chat.broadcast", map[string]any{
		"message": message,
	})
}

// SendMessage queues a player message and returns its action ID.
func (context *Context) SendMessage(playerUUID, message string) string {
	if context.client {
		return ""
	}
	return context.queueAction("minecraft:chat.player", map[string]any{
		"playerUuid": playerUUID,
		"message":    message,
	})
}

// DisplayClientMessage queues a local chat message and returns its action ID.
func (context *Context) DisplayClientMessage(message string) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.chat.display", map[string]any{
		"message": message,
	})
}

func (context *Context) OpenClientBrowser(url string) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.browser.open", map[string]any{"url": url})
}

func (context *Context) JoinClientSession(serverID string) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.session.join", map[string]any{"serverId": serverID})
}

func (context *Context) SaveClientConfig(config any) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.config.save", map[string]any{"config": config})
}

// OpenClientScreen opens or updates a client-local Minecraft form and returns
// its action ID. It returns an empty string in a server runtime.
func (context *Context) OpenClientScreen(screen ClientScreen) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.screen.open", screen)
}

// CloseClientScreen closes screenID if it belongs to this plugin and returns
// its action ID. It returns an empty string in a server runtime.
func (context *Context) CloseClientScreen(screenID string) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.screen.close", map[string]any{"id": screenID})
}

// CaptureClientScreen queues a framebuffer capture and returns its action ID.
// Pixels arrive later through ClientScreenCaptureHandler. It returns an empty
// string in a server runtime.
func (context *Context) CaptureClientScreen() string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.screen.capture", map[string]any{})
}

// SetHUD replaces the retained client HUD and returns its action ID.
func (context *Context) SetHUD(elements ...HUDElement) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.hud.set", map[string]any{
		"elements": append([]HUDElement(nil), elements...),
	})
}

// ClearHUD removes every HUD element retained for this client plugin.
func (context *Context) ClearHUD() string {
	return context.SetHUD()
}

// RenderHUD creates or updates one retained HUD element and returns its action ID.
func (context *Context) RenderHUD(element HUDElement) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.hud.upsert", map[string]any{
		"element": element,
	})
}

// RemoveHUD removes one retained element and returns its action ID.
func (context *Context) RemoveHUD(id string) string {
	if !context.client {
		return ""
	}
	return context.queueAction("minecraft:client.hud.remove", map[string]any{
		"id": id,
	})
}

func (context *Context) queueAction(actionType string, payload any) string {
	id := fmt.Sprintf("action-%d", callSequence.Add(1))
	context.actions = append(context.actions, ActionRequest{ID: id, Type: actionType, Payload: payload})
	return id
}

// Named returns a copy with the ID used by RenderHUD and RemoveHUD.
func (element HUDElement) Named(id string) HUDElement {
	element.ID = id
	return element
}

// Temporary returns a copy that disappears automatically after duration.
func (element HUDElement) Temporary(duration time.Duration) HUDElement {
	if duration <= 0 {
		element.DurationMillis = 0
	} else {
		element.DurationMillis = duration.Milliseconds()
		if element.DurationMillis == 0 {
			element.DurationMillis = 1
		}
	}
	return element
}

// HUDText constructs a text primitive. Coordinates are GUI-scaled pixels.
func HUDText(text string, x, y int, color uint32, shadow bool, anchor HUDAnchor) HUDElement {
	return HUDElement{
		Type: "text", Text: text, X: x, Y: y,
		Color: color, Shadow: shadow, Anchor: anchor,
	}
}

// HUDRectangle constructs a filled rectangle primitive.
func HUDRectangle(x, y, width, height int, color uint32, anchor HUDAnchor) HUDElement {
	return HUDElement{
		Type: "rectangle", X: x, Y: y, Width: width, Height: height,
		Color: color, Anchor: anchor,
	}
}

// SystemCall queues one of the system calls built into the bridge.
func (context *Context) SystemCall(callType SystemCallType, payload any) string {
	if context.client {
		return ""
	}
	return context.queueSystemCall(string(callType), payload)
}

// CustomSystemCall queues a system call registered by another mod.
func (context *Context) CustomSystemCall(name string, payload any) string {
	if context.client {
		return ""
	}
	return context.queueSystemCall(name, payload)
}

// GetServerInfo requests common server state and returns its request ID.
func (context *Context) GetServerInfo() string {
	return context.SystemCall(SystemCallServerInfo, struct{}{})
}

// GetPlayer requests an online player by UUID and returns its request ID.
func (context *Context) GetPlayer(playerUUID string) string {
	return context.SystemCall(SystemCallPlayerGet, PlayerGetRequest{PlayerUUID: playerUUID})
}

// GetBlock requests a loaded block and returns its request ID.
func (context *Context) GetBlock(block BlockReference) string {
	return context.SystemCall(SystemCallBlockGet, block)
}

// GetEntity requests an entity by UUID or runtime ID and returns its request ID.
// The EntitySnapshot is delivered to SystemCallResultHandler and can be decoded
// with DecodeSystemCallResult[EntitySnapshot].
func (context *Context) GetEntity(entity GetEntityRequest) string {
	return context.SystemCall(SystemCallGetEntity, entity)
}

// queueSystemCall queues a system call to be executed by the bridge.
func (context *Context) queueSystemCall(name string, payload any) string {
	id := fmt.Sprintf("call-%d", callSequence.Add(1))
	context.systemCalls = append(context.systemCalls, SystemCallRequest{
		ID:      id,
		Name:    name,
		Payload: payload,
	})
	return id
}

// SubscribeSnapshot queues a snapshot subscription to be executed by the bridge.
func (context *Context) SubscribeSnapshot(entities bool, blocks ...BlockReference) {
	if context.client {
		return
	}
	context.snapshot = &SnapshotSubscription{
		Entities: entities,
		Blocks:   append([]BlockReference(nil), blocks...),
	}
}

// Log queues a log message to be sent to the server.
func (context *Context) Log(level, message string) {
	context.logs = append(context.logs, LogEntry{
		Stream:             "sdk",
		Level:              level,
		Message:            message,
		TimestampUnixMilli: time.Now().UnixMilli(),
	})
}

// Kill queues the built-in entity kill call and returns its request ID.
// The result is delivered to SystemCallResultHandler. Client runtimes reject it.
func (context *Context) Kill(entityUUID string) string {
	return context.SystemCall(SystemCallKillEntity, GetEntityRequest{UUID: entityUUID})
}
