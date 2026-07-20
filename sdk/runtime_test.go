package sdk

import (
	"encoding/json"
	"errors"
	"testing"
)

type testPlugin struct{}

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
