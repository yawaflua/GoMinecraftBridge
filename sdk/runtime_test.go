package sdk

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"testing"
	"time"
)

type testPlugin struct{}

type clientFeatureTestPlugin struct {
	capture ClientScreenCapture
	event   ClientScreenEvent
}

func (plugin *clientFeatureTestPlugin) Metadata() Metadata {
	return Metadata{ID: "client_feature", Name: "Client feature", Version: "1.0.0", Environment: PluginEnvironmentClient}
}

func (plugin *clientFeatureTestPlugin) ClientScreenCaptured(_ *Context, capture ClientScreenCapture) error {
	plugin.capture = capture
	return nil
}

func (plugin *clientFeatureTestPlugin) ClientScreenEvent(_ *Context, event ClientScreenEvent) error {
	plugin.event = event
	return nil
}

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

func TestClientScreenActionsAreClientOnly(t *testing.T) {
	screen := ClientScreen{ID: "payment", Title: "Payment"}
	server := &Context{}
	server.OpenClientScreen(screen)
	server.CloseClientScreen(screen.ID)
	server.CaptureClientScreen()
	if len(server.actions) != 0 {
		t.Fatalf("server context queued client actions: %#v", server.actions)
	}

	client := &Context{client: true}
	client.OpenClientScreen(screen)
	client.CloseClientScreen(screen.ID)
	client.CaptureClientScreen()
	if len(client.actions) != 3 || client.actions[0].Type != "minecraft:client.screen.open" ||
		client.actions[1].Type != "minecraft:client.screen.close" ||
		client.actions[2].Type != "minecraft:client.screen.capture" {
		t.Fatalf("unexpected client screen actions: %#v", client.actions)
	}
}

func TestClientScopeMarkerCoversSharedOperations(t *testing.T) {
	input := []byte(`{"runtimeEnvironment":"client","config":{}}`)
	if !dispatchRunsOnClient(OperationConfigUpdate, input) {
		t.Fatal("client-scoped config update was treated as a server callback")
	}
	if dispatchRunsOnClient(OperationConfigUpdate, []byte(`{"config":{}}`)) {
		t.Fatal("unscoped config update was treated as a client callback")
	}
}

func TestDispatchClientScreenEventAndCapture(t *testing.T) {
	plugin := &clientFeatureTestPlugin{}
	pluginMu.Lock()
	registeredPlugin = plugin
	pluginMu.Unlock()

	event, _ := json.Marshal(ClientScreenEvent{ScreenID: "payment", Type: "button", ButtonID: "submit"})
	Dispatch(OperationClientScreenEvent, event)
	if plugin.event.ButtonID != "submit" {
		t.Fatalf("screen event was not dispatched: %#v", plugin.event)
	}

	pixels := []byte{1, 2, 3, 4, 5, 6, 7, 8}
	frame := make([]byte, clientCaptureHeaderSize+len(pixels))
	copy(frame, "GMBC")
	frame[4], frame[5] = 1, 1
	binary.LittleEndian.PutUint32(frame[8:12], 2)
	binary.LittleEndian.PutUint32(frame[12:16], 1)
	binary.LittleEndian.PutUint32(frame[16:20], 8)
	binary.LittleEndian.PutUint32(frame[20:24], uint32(len(pixels)))
	copy(frame[clientCaptureHeaderSize:], pixels)
	Dispatch(OperationClientScreenCapture, frame)
	if plugin.capture.Width != 2 || plugin.capture.Height != 1 || !jsonBytesEqual(plugin.capture.Pixels, pixels) {
		t.Fatalf("screen capture was not dispatched: %#v", plugin.capture)
	}
}

func jsonBytesEqual(left, right []byte) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
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
