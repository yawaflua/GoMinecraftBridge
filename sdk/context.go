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
}

// Broadcast queues a message to be sent to all players.
func (context *Context) Broadcast(message string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:chat.broadcast",
		Payload: map[string]any{
			"message": message,
		},
	})
}

// SendMessage queues a message to be sent to a player.
func (context *Context) SendMessage(playerUUID, message string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:chat.player",
		Payload: map[string]any{
			"playerUuid": playerUUID,
			"message":    message,
		},
	})
}

// DisplayClientMessage appends a local-only message to the Minecraft client
// chat. Client runtimes reject server action types such as SendMessage.
func (context *Context) DisplayClientMessage(message string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:client.chat.display",
		Payload: map[string]any{
			"message": message,
		},
	})
}

// SetHUD replaces all HUD elements retained for this client plugin. Elements
// remain visible until the next SetHUD/ClearHUD call or until the plugin stops.
func (context *Context) SetHUD(elements ...HUDElement) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:client.hud.set",
		Payload: map[string]any{
			"elements": append([]HUDElement(nil), elements...),
		},
	})
}

// ClearHUD removes every HUD element retained for this client plugin.
func (context *Context) ClearHUD() {
	context.SetHUD()
}

// RenderHUD creates or updates one retained HUD element. The element must have
// an ID, for example HUDText(...).Named("status").
func (context *Context) RenderHUD(element HUDElement) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:client.hud.upsert",
		Payload: map[string]any{
			"element": element,
		},
	})
}

// RemoveHUD removes one retained element by its plugin-local ID.
func (context *Context) RemoveHUD(id string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:client.hud.remove",
		Payload: map[string]any{
			"id": id,
		},
	})
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
	return context.queueSystemCall(string(callType), payload)
}

// CustomSystemCall queues a system call registered by another mod.
func (context *Context) CustomSystemCall(name string, payload any) string {
	return context.queueSystemCall(name, payload)
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
