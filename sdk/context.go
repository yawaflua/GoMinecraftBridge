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

func (context *Context) Broadcast(message string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:chat.broadcast",
		Payload: map[string]any{
			"message": message,
		},
	})
}

func (context *Context) SendMessage(playerUUID, message string) {
	context.actions = append(context.actions, ActionRequest{
		Type: "minecraft:chat.player",
		Payload: map[string]any{
			"playerUuid": playerUUID,
			"message":    message,
		},
	})
}

func (context *Context) SystemCall(name string, payload any) string {
	id := fmt.Sprintf("call-%d", callSequence.Add(1))
	context.systemCalls = append(context.systemCalls, SystemCallRequest{
		ID:      id,
		Name:    name,
		Payload: payload,
	})
	return id
}

func (context *Context) SubscribeSnapshot(entities bool, blocks ...BlockReference) {
	context.snapshot = &SnapshotSubscription{
		Entities: entities,
		Blocks:   append([]BlockReference(nil), blocks...),
	}
}

func (context *Context) Log(level, message string) {
	context.logs = append(context.logs, LogEntry{
		Stream:             "sdk",
		Level:              level,
		Message:            message,
		TimestampUnixMilli: time.Now().UnixMilli(),
	})
}
