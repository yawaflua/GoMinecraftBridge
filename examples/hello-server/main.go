package main

import (
	"fmt"
	"math/rand"
	"strings"

	"github.com/yawaflua/GoMinecraftBridge/sdk"
	"github.com/yawaflua/GoMinecraftBridge/sdk/server"
)

type Plugin struct{}

func (p Plugin) AllowChat(context *server.Context, event sdk.ChatEvent) (bool, error) {
	fmt.Println(event.Message)
	if strings.Contains(event.Message, "fuck") {
		context.SendMessage(event.PlayerUUID, "Asur leagid at ze!")
		return false, nil
	}
	return true, nil
}

func (p Plugin) AfterDamage(context *server.Context, event sdk.AfterDamageEvent) error {
	roll := rand.Intn(100)
	fmt.Println(roll)
	if !event.Blocked && roll < 67 {
		context.Kill(event.Entity.UUID)
		if event.AttackerUUID == nil {
			return nil
		}
		context.SendMessage(*event.AttackerUUID, "CRIT!")
	}
	return nil
}

func (p Plugin) Init(context *server.Context, event sdk.InitEvent) error {
	context.SubscribeSnapshot(true, []sdk.BlockReference{
		{
			Dimension: "minecraft:overworld",
			X:         0,
			Y:         64,
			Z:         0,
		},
	}...)
	context.Log("info", fmt.Sprintf(
		"capabilities: after_damage=%t allow_chat=%t",
		event.Supports(sdk.CapabilityAfterDamage),
		event.Supports(sdk.CapabilityAllowChat),
	))
	fmt.Println("Subscribed on block 0 64 0 with entities")
	return nil
}

func (p Plugin) PlayerJoin(context *server.Context, event sdk.PlayerConnectionEvent) error {
	context.Log("info", fmt.Sprintf("player joined: %s (%s)", event.Player.Name, event.Player.UUID))
	context.Broadcast(fmt.Sprintf("Welcome, %s!", event.Player.Name))
	return nil
}

func (p Plugin) PlayerDisconnect(context *server.Context, event sdk.PlayerConnectionEvent) error {
	context.Log("info", fmt.Sprintf("player disconnected: %s (%s)", event.Player.Name, event.Player.UUID))
	context.Broadcast(fmt.Sprintf("%s left the server", event.Player.Name))
	return nil
}

func metadata() sdk.Metadata {
	return sdk.Metadata{
		ID:             "hello-server",
		Name:           "HelloServer",
		Version:        "v0.2.0",
		Description:    "Just some userless functions",
		Authors:        []string{"yawaflua"},
		Website:        "https://bm.ywfl.dev",
		License:        "MIT",
		ConfigSchema:   nil,
		ConfigWritable: false,
	}
}

func init() {
	server.Register(metadata(), &Plugin{})

	fmt.Println("HelloServer init")
}
func main() {}
