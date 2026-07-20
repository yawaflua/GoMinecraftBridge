package sdk

import "testing"

func TestSystemCallUsesBuiltInType(t *testing.T) {
	context := &Context{}
	runtimeID := 42
	id := context.SystemCall(SystemCallGetEntity, GetEntityRequest{RuntimeID: &runtimeID})

	if id == "" {
		t.Fatal("SystemCall returned an empty id")
	}
	if len(context.systemCalls) != 1 {
		t.Fatalf("got %d queued calls, want 1", len(context.systemCalls))
	}
	if got := context.systemCalls[0].Name; got != "minecraft:get_entity" {
		t.Fatalf("queued call name = %q, want %q", got, "minecraft:get_entity")
	}
}

func TestCustomSystemCallKeepsExtensionPoint(t *testing.T) {
	context := &Context{}
	context.CustomSystemCall("example:claim.owner", nil)

	if len(context.systemCalls) != 1 {
		t.Fatalf("got %d queued calls, want 1", len(context.systemCalls))
	}
	if got := context.systemCalls[0].Name; got != "example:claim.owner" {
		t.Fatalf("queued call name = %q, want %q", got, "example:claim.owner")
	}
}
