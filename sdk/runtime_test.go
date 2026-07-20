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
