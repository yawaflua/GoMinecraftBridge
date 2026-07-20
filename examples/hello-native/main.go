package main

import (
	"encoding/json"
	"fmt"

	"github.com/yawaflua/GoMinecraftBridge/sdk"
)

type helloPlugin struct{}

func (helloPlugin) Metadata() sdk.Metadata {
	return sdk.Metadata{
		ID:          "hello_native",
		Name:        "Hello Native",
		Version:     "0.1.0",
		Description: "Native Go plugin example for Go Minecraft Bridge",
		Authors:     []string{"yawaflua"},
		Environment: sdk.PluginEnvironmentBoth,
		ConfigSchema: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"greeting": map[string]any{
					"type":    "string",
					"default": "Hello from Go!",
				},
			},
		},
	}
}

func (helloPlugin) Init(context *sdk.Context, event sdk.InitEvent) error {
	fmt.Printf("initialized for Minecraft %s; data=%s\n", event.MinecraftVersion, event.DataDirectory)
	if event.RuntimeEnvironment == sdk.PluginEnvironmentServer {
		context.SubscribeSnapshot(true, sdk.BlockReference{
			Dimension: "minecraft:overworld",
			X:         0,
			Y:         64,
			Z:         0,
		})
		// When you subscribe to a snapshot, you will receive a snapshot event every tick.
		//In that snapshot event, you can access the current state of the world, block that you are subscribed to
	}
	return nil
}

func (helloPlugin) ClientTick(context *sdk.Context, event sdk.ClientTickEvent) error {
	if event.Connected && event.Tick%1200 == 0 {
		context.DisplayClientMessage("Client Go runtime is active")
	}
	return nil
}

func (helloPlugin) Tick(context *sdk.Context, snapshot sdk.ServerSnapshot) error {
	if snapshot.Tick%200 == 0 {
		fmt.Printf("tick=%d entities=%d watched_blocks=%d\n", snapshot.Tick, len(snapshot.Entities), len(snapshot.Blocks))
		if len(snapshot.Entities) > 0 {
			runtimeID := snapshot.Entities[0].RuntimeID
			context.SystemCall(sdk.SystemCallGetEntity, sdk.GetEntityRequest{RuntimeID: &runtimeID})

		}
	}
	return nil
}

func (helloPlugin) Chat(context *sdk.Context, event sdk.ChatEvent) error {
	if event.Message != "!go" {
		return nil
	}

	context.SendMessage(event.PlayerUUID, "Hello from a native Go plugin!")
	context.SystemCall(sdk.SystemCallServerInfo, map[string]any{})
	return nil
}

func (helloPlugin) Death(context *sdk.Context, event sdk.DeathEvent) error {
	context.Broadcast(fmt.Sprintf("[Go] %s died (%s)", event.Entity.Name, event.DamageType))
	return nil
}

func (helloPlugin) SystemCallResult(context *sdk.Context, result sdk.SystemCallResult) error {
	if !result.Success {
		return fmt.Errorf("system call %s failed: %s", result.Name, result.Error)
	}

	var value any
	if err := json.Unmarshal(result.Data, &value); err != nil {
		return err
	}
	fmt.Printf("system call %s result: %v\n", result.Name, value)
	return nil
}

func (helloPlugin) Deinit(context *sdk.Context, event sdk.DeinitEvent) error {
	// Intentionally omit the newline: the SDK flush barrier still assigns this
	// partial stdout line to the deinit response.
	fmt.Printf("deinit: %s", event.Reason)
	return nil
}

func init() {
	sdk.Register(helloPlugin{})
}

func main() {}
