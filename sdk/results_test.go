package sdk

import (
	"encoding/json"
	"errors"
	"testing"
)

func TestDecodeSystemCallResult(t *testing.T) {
	result := SystemCallResult{
		ID: "call-1", Name: string(SystemCallServerInfo), Success: true,
		Data: json.RawMessage(`{"tick":42,"dedicated":true,"onlinePlayers":3}`),
	}
	info, err := DecodeSystemCallResult[ServerInfo](result)
	if err != nil {
		t.Fatal(err)
	}
	if info.Tick != 42 || !info.Dedicated || info.OnlinePlayers != 3 {
		t.Fatalf("unexpected server info: %#v", info)
	}
}

func TestDecodeSystemCallFailure(t *testing.T) {
	_, err := DecodeSystemCallResult[EntitySnapshot](SystemCallResult{
		ID: "call-2", Name: string(SystemCallGetEntity), Error: "not available",
	})
	var callErr *SystemCallError
	if !errors.As(err, &callErr) || callErr.ID != "call-2" || callErr.Message != "not available" {
		t.Fatalf("unexpected error: %v", err)
	}
}
