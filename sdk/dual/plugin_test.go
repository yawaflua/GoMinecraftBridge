package dual_test

import (
	"encoding/json"
	"testing"

	"github.com/yawaflua/GoMinecraftBridge/sdk"
	"github.com/yawaflua/GoMinecraftBridge/sdk/client"
	"github.com/yawaflua/GoMinecraftBridge/sdk/dual"
	"github.com/yawaflua/GoMinecraftBridge/sdk/server"
)

type serverPart struct {
	initialized int
}

func (plugin *serverPart) Init(_ *server.Context, _ sdk.InitEvent) error {
	plugin.initialized++
	return nil
}

type clientPart struct {
	initialized int
	updates     int
}

func (plugin *clientPart) Init(_ *client.Context, _ sdk.InitEvent) error {
	plugin.initialized++
	return nil
}

func (plugin *clientPart) Tick(context *client.Context, _ sdk.ClientTickEvent) error {
	context.DisplayMessage("client")
	return nil
}

func (plugin *clientPart) ConfigUpdated(_ *client.Context, _ sdk.ConfigUpdateEvent) error {
	plugin.updates++
	return nil
}

func TestRegisterRoutesEachRuntimeToItsTypedPart(t *testing.T) {
	serverPlugin := &serverPart{}
	clientPlugin := &clientPart{}
	dual.Register(sdk.Metadata{
		ID: "dual_test", Name: "Dual test", Version: "1.0.0", ConfigSchema: map[string]any{"type": "object"},
	}, serverPlugin, clientPlugin)

	serverInit, _ := json.Marshal(sdk.InitEvent{RuntimeEnvironment: sdk.PluginEnvironmentServer})
	clientInit, _ := json.Marshal(sdk.InitEvent{RuntimeEnvironment: sdk.PluginEnvironmentClient})
	assertOK(t, sdk.Dispatch(sdk.OperationInit, serverInit))
	assertOK(t, sdk.Dispatch(sdk.OperationInit, clientInit))
	if serverPlugin.initialized != 1 || clientPlugin.initialized != 1 {
		t.Fatalf("unexpected init routing: server=%d client=%d", serverPlugin.initialized, clientPlugin.initialized)
	}

	var tick struct {
		Status  string              `json:"status"`
		Actions []sdk.ActionRequest `json:"actions"`
	}
	input, _ := json.Marshal(sdk.ClientTickEvent{Connected: true})
	if err := json.Unmarshal(sdk.Dispatch(sdk.OperationClientTick, input), &tick); err != nil {
		t.Fatal(err)
	}
	if tick.Status != "ok" || len(tick.Actions) != 1 || tick.Actions[0].Type != "minecraft:client.chat.display" {
		t.Fatalf("unexpected client tick response: %#v", tick)
	}

	if status(t, sdk.Dispatch(sdk.OperationConfigUpdate, []byte(`{"config":{"value":1}}`))) != "error" {
		t.Fatal("server config update used the client-only config handler")
	}
	if status(t, sdk.Dispatch(
		sdk.OperationConfigUpdate,
		[]byte(`{"runtimeEnvironment":"client","config":{"value":1}}`),
	)) != "ok" || clientPlugin.updates != 1 {
		t.Fatalf("client config update was not routed: updates=%d", clientPlugin.updates)
	}
}

func status(t *testing.T, output []byte) string {
	t.Helper()
	var result struct {
		Status string `json:"status"`
	}
	if err := json.Unmarshal(output, &result); err != nil {
		t.Fatal(err)
	}
	return result.Status
}

func assertOK(t *testing.T, output []byte) {
	t.Helper()
	var result struct {
		Status string `json:"status"`
		Error  string `json:"error"`
	}
	if err := json.Unmarshal(output, &result); err != nil {
		t.Fatal(err)
	}
	if result.Status != "ok" {
		t.Fatalf("dispatch failed: %s", result.Error)
	}
}
