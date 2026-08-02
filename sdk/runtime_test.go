package sdk

import (
	"encoding/json"
	"errors"
	"testing"
	"time"
)

type testPlugin struct{}

type decisionTestPlugin struct {
	testPlugin
}

func (decisionTestPlugin) AllowDamage(_ *Context, event AllowDamageEvent) (bool, error) {
	return event.Amount < 5, nil
}

func (decisionTestPlugin) AllowDeath(_ *Context, _ AllowDeathEvent) (bool, error) {
	return false, nil
}

type testConfig struct {
	Greeting string `json:"greeting"`
	Enabled  bool   `json:"enabled"`
}

type configurableTestPlugin struct {
	config  *testConfig
	updates int
}

func (plugin *configurableTestPlugin) Metadata() Metadata {
	return Metadata{
		ID: "configurable_test", Name: "Configurable", Version: "1.0.0",
		ConfigSchema: plugin.config,
	}
}

func (plugin *configurableTestPlugin) ConfigUpdated(_ *Context, _ ConfigUpdateEvent) error {
	plugin.updates++
	return nil
}

func (testPlugin) Metadata() Metadata {
	return Metadata{ID: "test_plugin", Name: "Test", Version: "1.0.0"}
}

func (testPlugin) Chat(context *Context, event ChatEvent) error {
	if event.Message == "panic" {
		panic("expected panic")
	}
	if event.Message == "error" {
		return errors.New("expected error")
	}
	context.SendMessage(event.PlayerUUID, "received")
	return nil
}

func (testPlugin) ClientTick(context *Context, event ClientTickEvent) error {
	if event.Connected {
		context.DisplayClientMessage(event.PlayerName)
	}
	return nil
}

func TestDispatch(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = testPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(ChatEvent{PlayerUUID: "player", Message: "hello"})
	var got response
	if err := json.Unmarshal(Dispatch(OperationChat, input), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "ok" || len(got.Actions) != 1 {
		t.Fatalf("unexpected response: %#v", got)
	}
}

func TestDispatchAllowDamageReturnsDecision(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = decisionTestPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(AllowDamageEvent{Amount: 10})
	var got response
	if err := json.Unmarshal(Dispatch(OperationAllowDamage, input), &got); err != nil {
		t.Fatal(err)
	}
	allowed, ok := got.Data.(bool)
	if got.Status != "ok" || !ok || allowed {
		t.Fatalf("unexpected allow-damage decision: %#v", got)
	}
}

func TestDispatchDecisionDefaultsToAllow(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = testPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(AllowDeathEvent{})
	var got response
	if err := json.Unmarshal(Dispatch(OperationAllowDeath, input), &got); err != nil {
		t.Fatal(err)
	}
	allowed, ok := got.Data.(bool)
	if got.Status != "ok" || !ok || !allowed {
		t.Fatalf("unexpected default decision: %#v", got)
	}
}

func TestMetadataDefaultsToServerEnvironment(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = testPlugin{}
	pluginMu.Unlock()

	var got struct {
		Data Metadata `json:"data"`
	}
	if err := json.Unmarshal(Dispatch(OperationMetadata, nil), &got); err != nil {
		t.Fatal(err)
	}
	if got.Data.Environment != PluginEnvironmentServer {
		t.Fatalf("environment = %q, want %q", got.Data.Environment, PluginEnvironmentServer)
	}
}

func TestMetadataSerializesLicense(t *testing.T) {
	metadata := Metadata{
		ID: "licensed", Name: "Licensed", Version: "1.0.0", License: "MIT",
	}

	encoded, err := json.Marshal(metadata)
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]any
	if err := json.Unmarshal(encoded, &fields); err != nil {
		t.Fatal(err)
	}
	if fields["license"] != "MIT" {
		t.Fatalf("license metadata was not serialized: %s", encoded)
	}
	if _, legacy := fields["licence"]; legacy {
		t.Fatalf("metadata used legacy licence spelling: %s", encoded)
	}
}

func TestMetadataMarksPointerConfigWritable(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = &configurableTestPlugin{config: &testConfig{Greeting: "old"}}
	pluginMu.Unlock()

	var got struct {
		Data Metadata `json:"data"`
	}
	if err := json.Unmarshal(Dispatch(OperationMetadata, nil), &got); err != nil {
		t.Fatal(err)
	}
	if !got.Data.ConfigWritable {
		t.Fatal("pointer-backed config was not marked writable")
	}
}

func TestDispatchClientTick(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = testPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(ClientTickEvent{Connected: true, PlayerName: "Client player"})
	var got response
	if err := json.Unmarshal(Dispatch(OperationClientTick, input), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "ok" || len(got.Actions) != 1 {
		t.Fatalf("unexpected client tick response: %#v", got)
	}
	if got.Actions[0].Type != "minecraft:client.chat.display" {
		t.Fatalf("action type = %q", got.Actions[0].Type)
	}
}

func TestSetHUDAction(t *testing.T) {
	context := &Context{}
	context.SetHUD(
		HUDText("hello", 4, 6, 0xffffffff, true, HUDTopLeft),
		HUDRectangle(8, 10, 40, 12, 0x80000000, HUDBottomRight),
	)
	if len(context.actions) != 1 || context.actions[0].Type != "minecraft:client.hud.set" {
		t.Fatalf("unexpected HUD actions: %#v", context.actions)
	}
	payload, ok := context.actions[0].Payload.(map[string]any)
	if !ok {
		t.Fatalf("unexpected HUD payload: %#v", context.actions[0].Payload)
	}
	elements, ok := payload["elements"].([]HUDElement)
	if !ok || len(elements) != 2 || elements[0].Text != "hello" || elements[1].Width != 40 {
		t.Fatalf("unexpected HUD elements: %#v", payload["elements"])
	}
}

func TestRenderAndRemoveTemporaryHUDAction(t *testing.T) {
	context := &Context{}
	element := HUDText("temporary", 2, 3, 0xffffffff, true, HUDTopLeft).
		Named("notice").
		Temporary(1500 * time.Millisecond)
	context.RenderHUD(element)
	context.RemoveHUD("notice")

	if len(context.actions) != 2 {
		t.Fatalf("unexpected action count: %#v", context.actions)
	}
	if context.actions[0].Type != "minecraft:client.hud.upsert" {
		t.Fatalf("unexpected render action: %#v", context.actions[0])
	}
	payload := context.actions[0].Payload.(map[string]any)
	rendered := payload["element"].(HUDElement)
	if rendered.ID != "notice" || rendered.DurationMillis != 1500 {
		t.Fatalf("unexpected temporary element: %#v", rendered)
	}
	if context.actions[1].Type != "minecraft:client.hud.remove" {
		t.Fatalf("unexpected remove action: %#v", context.actions[1])
	}
}

func TestDispatchConfigUpdateUpdatesTypedConfig(t *testing.T) {
	plugin := &configurableTestPlugin{config: &testConfig{Greeting: "old"}}
	pluginMu.Lock()
	registeredPlugin = plugin
	pluginMu.Unlock()

	input := []byte(`{"config":{"greeting":"new","enabled":true}}`)
	var got response
	if err := json.Unmarshal(Dispatch(OperationConfigUpdate, input), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "ok" {
		t.Fatalf("config update failed: %#v", got)
	}
	if plugin.config.Greeting != "new" || !plugin.config.Enabled || plugin.updates != 1 {
		t.Fatalf("config was not updated: %#v, updates=%d", plugin.config, plugin.updates)
	}
	data, ok := got.Data.(map[string]any)
	if !ok || data["greeting"] != "new" || data["enabled"] != true {
		t.Fatalf("response data = %#v", got.Data)
	}
}

func TestDispatchConfigUpdateRejectsStructValueWithoutHandler(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = pluginWithValueConfig{}
	pluginMu.Unlock()

	var got response
	if err := json.Unmarshal(Dispatch(OperationConfigUpdate, []byte(`{"config":{"greeting":"new"}}`)), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "error" {
		t.Fatalf("expected an error for a non-pointer config: %#v", got)
	}
}

type pluginWithValueConfig struct{}

func (pluginWithValueConfig) Metadata() Metadata {
	return Metadata{
		ID: "value_config", Name: "Value Config", Version: "1.0.0",
		ConfigSchema: testConfig{Greeting: "old"},
	}
}

func TestDispatchRecoversPanic(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = testPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(ChatEvent{Message: "panic"})
	var got response
	if err := json.Unmarshal(Dispatch(OperationChat, input), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "panic" || got.Stack == "" {
		t.Fatalf("panic was not captured: %#v", got)
	}
}
