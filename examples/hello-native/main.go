package main

import (
	"fmt"
	"strings"
	"time"

	"github.com/yawaflua/GoMinecraftBridge/sdk"
	"github.com/yawaflua/GoMinecraftBridge/sdk/client"
	"github.com/yawaflua/GoMinecraftBridge/sdk/dual"
	"github.com/yawaflua/GoMinecraftBridge/sdk/server"
)

type serverPlugin struct {
	capabilities []sdk.Capability
}
type TestEnum int8

const (
	TestEnumChat TestEnum = iota
	TestEnumActionBar
	TestEnumToast
)

type clientPlugin struct {
	hudDemoTick       int64
	hudVisible        bool
	customScreenShown bool
}

func (plugin serverPlugin) AfterDamage(context *server.Context, event sdk.AfterDamageEvent) error {
	if !event.Entity.Player && event.Entity.Alive && event.AttackerUUID != nil {
		context.Kill(event.Entity.UUID)
		context.SendMessage(*event.AttackerUUID, "CRIT!")
	}
	return nil
}

type helloConfig struct {
	Greeting     string   `json:"greeting"`
	Enabled      bool     `json:"enabled"`
	MessageStyle string   `json:"messageStyle" gbm:"[\"chat\",\"action_bar\",\"toast\"]"`
	RepeatTicks  int      `json:"repeatTicks"`
	FavoriteTags []string `json:"favoriteTags"`
	TestEnum     TestEnum `json:"testEnum" gbm:"[0,1,2]"`
}

var config = &helloConfig{
	Greeting:     "Hello from Go!",
	Enabled:      true,
	MessageStyle: "chat",
	RepeatTicks:  1200,
	FavoriteTags: []string{"native", "go"},
	TestEnum:     TestEnumChat,
}

func metadata() sdk.Metadata {
	return sdk.Metadata{
		ID:           "hello_native",
		Name:         "Hello Native",
		Version:      "0.1.0",
		Description:  "Native Go plugin example for GBM",
		Authors:      []string{"yawaflua"},
		License:      "MIT",
		ConfigSchema: config,
		ClientKeyBindings: []sdk.ClientKeyBinding{
			{ID: "example", Name: "Hello from Go", DefaultKey: "key.keyboard.p"},
		},
	}
}

func configUpdated() error {
	fmt.Printf("configuration updated: enabled=%t greeting=%q\n", config.Enabled, config.Greeting)
	return nil
}

func (serverPlugin) ConfigUpdated(_ *server.Context, _ sdk.ConfigUpdateEvent) error {
	return configUpdated()
}

func (clientPlugin) ConfigUpdated(_ *client.Context, _ sdk.ConfigUpdateEvent) error {
	return configUpdated()
}

func (plugin *serverPlugin) Init(context *server.Context, event sdk.InitEvent) error {
	plugin.capabilities = append([]sdk.Capability(nil), event.Capabilities...)
	fmt.Printf(
		"initialized for Minecraft %s; data=%s; capabilities=%v\n",
		event.MinecraftVersion, event.DataDirectory, event.Capabilities,
	)
	context.SubscribeSnapshot(true, sdk.BlockReference{
		Dimension: "minecraft:overworld",
		X:         0,
		Y:         64,
		Z:         0,
	})
	return nil
}

func (clientPlugin) Init(_ *client.Context, event sdk.InitEvent) error {
	fmt.Printf(
		"initialized client for Minecraft %s; data=%s; capabilities=%v\n",
		event.MinecraftVersion, event.DataDirectory, event.Capabilities,
	)
	return nil
}

func (plugin *clientPlugin) Tick(context *client.Context, event sdk.ClientTickEvent) error {
	if config.Enabled && event.Connected && config.RepeatTicks > 0 && event.Tick%int64(config.RepeatTicks) == 0 {
		context.DisplayMessage(config.Greeting)
	}

	if !config.Enabled || !event.Connected {
		if plugin.hudVisible {
			// Remove every element owned by this plugin at once.
			context.ClearHUD()
		}
		plugin.hudDemoTick = 0
		plugin.hudVisible = false
		return nil
	}

	plugin.hudDemoTick++
	switch plugin.hudDemoTick {
	case 1:
		if !plugin.customScreenShown {
			context.OpenScreen(exampleCustomScreen())
			plugin.customScreenShown = true
		}

		// Render independent retained elements. Named IDs let later calls update
		// or remove one element without touching the others.
		context.RenderHUD(sdk.HUDRectangle(
			8, 8, 150, 38, 0x90000000, sdk.HUDTopLeft,
		).Named("hello-panel"))
		context.RenderHUD(sdk.HUDText(
			"GBM", 14, 13, 0xff55ff55, true, sdk.HUDTopLeft,
		).Named("hello-title"))
		context.RenderHUD(sdk.HUDText(
			"HUD tick: 1", 14, 29, 0xffffffff, true, sdk.HUDTopLeft,
		).Named("hello-status"))

		// This element removes itself after three seconds.
		context.RenderHUD(sdk.HUDText(
			"Temporary notification", 8, 8, 0xffffff55, true, sdk.HUDTopRight,
		).Named("hello-notice").Temporary(3 * time.Second))
		plugin.hudVisible = true

	case 20, 40, 60, 80, 100, 120, 140, 160, 180:
		// Rendering the same ID updates the existing element in place.
		context.RenderHUD(sdk.HUDText(
			fmt.Sprintf("HUD tick: %d", plugin.hudDemoTick),
			14, 29, 0xffffffff, true, sdk.HUDTopLeft,
		).Named("hello-status"))

	case 200:
		// Remove just the changing status line.
		context.RemoveHUD("hello-status")

	case 260:
		// Remove the remaining persistent elements individually.
		context.RemoveHUD("hello-title")
		context.RemoveHUD("hello-panel")
		plugin.hudVisible = false

	case 320:
		// Repeat the complete draw/update/remove demonstration.
		plugin.hudDemoTick = 0
	}
	return nil
}

func (clientPlugin) KeyPressed(context *client.Context, event sdk.ClientKeyEvent) error {
	if event.ID == "example" {
		context.DisplayMessage("P pressed in Hello Native")
	}
	return nil
}

func (clientPlugin) ScreenEvent(context *client.Context, event sdk.ClientScreenEvent) error {
	if event.ScreenID != "hello-custom" || event.Type != "button" {
		return nil
	}

	alias := event.Values["alias"]
	theme := event.Values["theme"]
	context.DisplayMessage(fmt.Sprintf(
		"Custom screen action %q: alias=%q theme=%q",
		event.ButtonID, alias, theme,
	))
	return nil
}

func exampleCustomScreen() sdk.ClientScreen {
	return sdk.ClientScreen{
		ID:    "hello-custom",
		Title: "Custom Go screen",
		Elements: []sdk.ClientScreenElement{
			{
				ID: "panel", Type: sdk.ClientScreenElementRectangle,
				Anchor: sdk.HUDCenter, X: 0, Y: 0, Width: 360, Height: 220,
				Color: 0xe0181820,
			},
			{
				ID: "heading", Type: sdk.ClientScreenElementText,
				Anchor: sdk.HUDCenter, X: 0, Y: -82,
				Text: "This layout and its coordinates come from Go", Color: 0xff55ff55, Shadow: true,
			},
			{
				ID: "alias-label", Type: sdk.ClientScreenElementText,
				Anchor: sdk.HUDCenter, X: 0, Y: -48,
				Text: "Alias", Color: 0xffdddddd,
			},
			{
				ID: "alias", Type: sdk.ClientScreenElementTextInput,
				Anchor: sdk.HUDCenter, X: 0, Y: -24, Width: 260, Height: 20,
				Placeholder: "Type anything", MaxLength: 48,
			},
			{
				ID: "theme", Type: sdk.ClientScreenElementSelect,
				Anchor: sdk.HUDCenter, X: 0, Y: 16, Width: 260, Height: 20,
				Value: "green",
				Options: []sdk.ClientScreenOption{
					{Value: "green", Label: "Green theme"},
					{Value: "purple", Label: "Purple theme"},
				},
			},
			{
				ID: "apply-background", Type: sdk.ClientScreenElementRectangle,
				Anchor: sdk.HUDCenter, X: -70, Y: 66, Width: 120, Height: 20,
				Color: 0xff286c45,
			},
			{
				ID: "apply-label", Type: sdk.ClientScreenElementText,
				Anchor: sdk.HUDCenter, X: -70, Y: 66,
				Text: "Apply custom hitbox", Color: 0xffffffff,
			},
			{
				ID: "apply", Type: sdk.ClientScreenElementHitbox,
				Anchor: sdk.HUDCenter, X: -70, Y: 66, Width: 120, Height: 20,
			},
			{
				ID: "done", Type: sdk.ClientScreenElementButton,
				Anchor: sdk.HUDCenter, X: 70, Y: 66, Width: 120, Height: 20,
				Text: "Done", Close: true,
			},
		},
	}
}

func (clientPlugin) ScreenCaptured(context *client.Context, capture sdk.ClientScreenCapture) error {
	context.Log("info", fmt.Sprintf(
		"captured screen: %dx%d stride=%d bytes=%d",
		capture.Width, capture.Height, capture.Stride, len(capture.Pixels),
	))
	return nil
}

func (serverPlugin) Tick(context *server.Context, snapshot sdk.ServerSnapshot) error {
	if snapshot.Tick%200 == 0 {
		fmt.Printf("tick=%d entities=%d watched_blocks=%d\n", snapshot.Tick, len(snapshot.Entities), len(snapshot.Blocks))
		if len(snapshot.Entities) > 0 {
			runtimeID := snapshot.Entities[0].RuntimeID
			context.GetEntity(sdk.GetEntityRequest{RuntimeID: &runtimeID})

		}
	}
	return nil
}

func (plugin *serverPlugin) Chat(context *server.Context, event sdk.ChatEvent) error {
	if event.Message == "!go capabilities" {
		context.SendMessage(event.PlayerUUID, fmt.Sprintf("GBM capabilities: %v", plugin.capabilities))
		return nil
	}
	if event.Message != "!go" {
		return nil
	}

	context.SendMessage(event.PlayerUUID, config.Greeting)
	context.GetServerInfo()
	return nil
}

type logContext interface {
	Log(level, message string)
}

func handleInteraction(context logContext, event sdk.InteractionEvent) error {
	if !event.Sneaking {
		return nil
	}
	if event.Block != nil && strings.Contains(event.Block.Block, "sign") {
		context.Log("info", fmt.Sprintf(
			"shift-clicked sign %s at %s %d %d %d; properties=%v",
			event.Block.Block, event.Block.Dimension,
			event.Block.X, event.Block.Y, event.Block.Z, event.Block.Properties,
		))
	}
	if event.Target != nil && event.Target.Player {
		context.Log("info", fmt.Sprintf(
			"shift-clicked player %s (%s) with %s",
			event.Target.Name, event.Target.UUID, event.Hand,
		))
	}
	return nil
}

func (serverPlugin) Interaction(context *server.Context, event sdk.InteractionEvent) error {
	return handleInteraction(context, event)
}

func (clientPlugin) Interaction(context *client.Context, event sdk.InteractionEvent) error {
	return handleInteraction(context, event)
}

func (serverPlugin) Death(context *server.Context, event sdk.DeathEvent) error {
	context.Broadcast(fmt.Sprintf("[Go] %s died (%s)", event.Entity.Name, event.DamageType))
	return nil
}

func (serverPlugin) SystemCallResult(context *server.Context, result sdk.SystemCallResult) error {
	value, err := sdk.DecodeSystemCallResult[any](result)
	if err != nil {
		return err
	}
	fmt.Printf("system call %s result: %v\n", result.Name, value)
	return nil
}

func handleActionResult(context logContext, result sdk.ActionResult) error {
	if !result.Success {
		context.Log("warn", fmt.Sprintf("action %s (%s) failed: %s", result.ID, result.Type, result.Error))
	}
	return nil
}

func (serverPlugin) ActionResult(context *server.Context, result sdk.ActionResult) error {
	return handleActionResult(context, result)
}

func (clientPlugin) ActionResult(context *client.Context, result sdk.ActionResult) error {
	return handleActionResult(context, result)
}

func deinit(event sdk.DeinitEvent) error {
	// Intentionally omit the newline: the SDK flush barrier still assigns this
	// partial stdout line to the deinit response.
	fmt.Printf("deinit: %s", event.Reason)
	return nil
}

func (serverPlugin) Deinit(_ *server.Context, event sdk.DeinitEvent) error {
	return deinit(event)
}

func (clientPlugin) Deinit(_ *client.Context, event sdk.DeinitEvent) error {
	return deinit(event)
}

func init() {
	dual.Register(metadata(), &serverPlugin{}, &clientPlugin{})
}

func main() {}
