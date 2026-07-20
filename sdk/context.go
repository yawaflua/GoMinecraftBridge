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
