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
	capture     ClientScreenCapture
	event       ClientScreenEvent
	interaction InteractionEvent
	action      ActionResult
}

type playerConnectionTestPlugin struct {
	testPlugin
	joined       PlayerConnectionEvent
	disconnected PlayerConnectionEvent
}

func (plugin *playerConnectionTestPlugin) PlayerJoin(_ *Context, event PlayerConnectionEvent) error {
	plugin.joined = event
	return nil
}

func (plugin *playerConnectionTestPlugin) PlayerDisconnect(_ *Context, event PlayerConnectionEvent) error {
	plugin.disconnected = event
	return nil
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

func (plugin *clientFeatureTestPlugin) Interaction(_ *Context, event InteractionEvent) error {
	plugin.interaction = event
	return nil
}

func (plugin *clientFeatureTestPlugin) ActionResult(_ *Context, result ActionResult) error {
	plugin.action = result
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

func (decisionTestPlugin) AllowChat(context *Context, event ChatEvent) (bool, error) {
	if event.Message == "blocked" {
		context.SendMessage(event.PlayerUUID, "message blocked")
		return false, nil
	}
	return true, nil
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
		ConfigSchema: plugin.config, Environment: PluginEnvironmentServer,
	}
}

func (plugin *configurableTestPlugin) ConfigUpdated(_ *Context, _ ConfigUpdateEvent) error {
	plugin.updates++
	return nil
}

func (testPlugin) Metadata() Metadata {
	return Metadata{
		ID: "test_plugin", Name: "Test", Version: "1.0.0", Environment: PluginEnvironmentServer,
	}
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

func TestDispatchAllowChatReturnsDecision(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = decisionTestPlugin{}
	pluginMu.Unlock()

	input, _ := json.Marshal(ChatEvent{PlayerUUID: "player", Message: "blocked"})
	var got response
	if err := json.Unmarshal(Dispatch(OperationAllowChat, input), &got); err != nil {
		t.Fatal(err)
	}
	allowed, ok := got.Data.(bool)
	if got.Status != "ok" || !ok || allowed || len(got.Actions) != 1 {
		t.Fatalf("unexpected allow-chat decision: %#v", got)
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

type missingEnvironmentPlugin struct{}

func (missingEnvironmentPlugin) Metadata() Metadata {
	return Metadata{ID: "missing_environment", Name: "Missing environment", Version: "1.0.0"}
}

func TestMetadataRequiresEnvironment(t *testing.T) {
	pluginMu.Lock()
	registeredPlugin = missingEnvironmentPlugin{}
	pluginMu.Unlock()

	var got struct {
		Status string `json:"status"`
	}
	if err := json.Unmarshal(Dispatch(OperationMetadata, nil), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "error" {
		t.Fatalf("missing environment response = %#v", got)
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
	for _, action := range client.actions {
		if action.ID == "" {
			t.Fatalf("client action has no request ID: %#v", action)
		}
	}
}

func TestContextRejectsOperationsFromTheWrongRuntime(t *testing.T) {
	server := &Context{}
	server.DisplayClientMessage("local")
	server.SetHUD(HUDText("hud", 0, 0, 0xffffffff, false, HUDTopLeft))
	server.RenderHUD(HUDText("hud", 0, 0, 0xffffffff, false, HUDTopLeft).Named("hud"))
	server.RemoveHUD("hud")
	if len(server.actions) != 0 {
		t.Fatalf("server context queued client actions: %#v", server.actions)
	}

	client := &Context{client: true}
	client.Broadcast("broadcast")
	client.SendMessage("player", "private")
	client.GetServerInfo()
	client.CustomSystemCall("example:test", struct{}{})
	client.SubscribeSnapshot(true)
	if len(client.actions) != 0 || len(client.systemCalls) != 0 || client.snapshot != nil {
		t.Fatalf("client context queued server work: actions=%#v calls=%#v snapshot=%#v",
			client.actions, client.systemCalls, client.snapshot)
	}
}

func TestKillUsesTypedSystemCall(t *testing.T) {
	context := &Context{}
	id := context.Kill("a4b505b8-4379-42ce-aed1-192b7698b20f")
	if id == "" || len(context.actions) != 0 || len(context.systemCalls) != 1 {
		t.Fatalf("unexpected kill request: id=%q actions=%#v calls=%#v", id, context.actions, context.systemCalls)
	}
	call := context.systemCalls[0]
	request, ok := call.Payload.(GetEntityRequest)
	if call.Name != string(SystemCallKillEntity) || !ok || request.UUID == "" {
		t.Fatalf("unexpected kill system call: %#v", call)
	}
}

func TestInitCapabilities(t *testing.T) {
	event := InitEvent{Capabilities: []Capability{
		CapabilityActionResults,
		CapabilityInteractionEvent,
		CapabilityPlayerJoinEvent,
		CapabilityPlayerDisconnectEvent,
	}}
	if !event.Supports(CapabilityInteractionEvent) || !event.Supports(CapabilityPlayerJoinEvent) ||
		!event.Supports(CapabilityPlayerDisconnectEvent) || event.Supports(CapabilityAllowDamage) {
		t.Fatalf("unexpected capability lookup: %#v", event.Capabilities)
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

func TestDispatchActionResult(t *testing.T) {
	plugin := &clientFeatureTestPlugin{}
	pluginMu.Lock()
	registeredPlugin = plugin
	pluginMu.Unlock()

	input, _ := json.Marshal(ActionResult{
		ID: "action-1", Type: "minecraft:chat.player", Success: false, Error: "offline",
	})
	Dispatch(OperationActionResult, input)
	if plugin.action.ID != "action-1" || plugin.action.Success || plugin.action.Error != "offline" {
		t.Fatalf("action result was not dispatched: %#v", plugin.action)
	}
}

func TestDispatchInteraction(t *testing.T) {
	plugin := &clientFeatureTestPlugin{}
	pluginMu.Lock()
	registeredPlugin = plugin
	pluginMu.Unlock()

	input := []byte(`{"action":"use_block","hand":"main_hand","sneaking":true,"player":{"uuid":"player"},"block":{"dimension":"minecraft:overworld","x":1,"y":2,"z":3,"block":"minecraft:oak_sign","properties":{}},"timestampUnixMilli":1}`)
	var got response
	if err := json.Unmarshal(Dispatch(OperationInteraction, input), &got); err != nil {
		t.Fatal(err)
	}
	if got.Status != "ok" || plugin.interaction.Block == nil || plugin.interaction.Block.Block != "minecraft:oak_sign" || !plugin.interaction.Sneaking {
		t.Fatalf("interaction was not dispatched: response=%#v event=%#v", got, plugin.interaction)
	}
}

func TestDispatchPlayerConnections(t *testing.T) {
	plugin := &playerConnectionTestPlugin{}
	pluginMu.Lock()
	registeredPlugin = plugin
	pluginMu.Unlock()

	event := PlayerConnectionEvent{
		Player:             EntitySnapshot{UUID: "player-id", Name: "Alex", Player: true},
		TimestampUnixMilli: 123,
	}
	input, _ := json.Marshal(event)
	var joined response
	if err := json.Unmarshal(Dispatch(OperationPlayerJoin, input), &joined); err != nil {
		t.Fatal(err)
	}
	var disconnected response
	if err := json.Unmarshal(Dispatch(OperationPlayerDisconnect, input), &disconnected); err != nil {
		t.Fatal(err)
	}
	if joined.Status != "ok" || disconnected.Status != "ok" ||
		plugin.joined.Player.UUID != "player-id" || plugin.disconnected.Player.Name != "Alex" {
		t.Fatalf("player events were not dispatched: join=%#v disconnect=%#v plugin=%#v", joined, disconnected, plugin)
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
	context := &Context{client: true}
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
	context := &Context{client: true}
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
		ConfigSchema: testConfig{Greeting: "old"}, Environment: PluginEnvironmentServer,
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
