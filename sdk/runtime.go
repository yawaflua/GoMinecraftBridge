package sdk

import (
	"encoding/json"
	"errors"
	"fmt"
	"runtime/debug"
	"sync"
)

var (
	pluginMu         sync.RWMutex
	registeredPlugin Plugin
)

// Register registers a plugin with the server.
func Register(plugin Plugin) {
	if plugin == nil {
		panic("sdk: cannot register a nil plugin")
	}

	pluginMu.Lock()
	defer pluginMu.Unlock()
	if registeredPlugin != nil {
		panic("sdk: a plugin is already registered")
	}
	registeredPlugin = plugin
	enableOutputCapture()
}

// Dispatch dispatches a plugin operation to the registered plugin.
func Dispatch(operation int, input []byte) (output []byte) {
	context := &Context{}
	result := response{Status: "ok"}

	defer func() {
		if recovered := recover(); recovered != nil {
			result.Status = "panic"
			result.Error = fmt.Sprint(recovered)
			result.Stack = string(debug.Stack())
		}

		result.Logs = append(result.Logs, context.logs...)
		result.Logs = append(result.Logs, drainCapturedLogs()...)
		result.Actions = context.actions
		result.SystemCalls = context.systemCalls
		result.Snapshot = context.snapshot

		encoded, err := json.Marshal(result)
		if err != nil {
			output = []byte(`{"status":"panic","error":"cannot encode plugin response"}`)
			return
		}
		output = encoded
	}()

	plugin := currentPlugin()
	var err error

	switch operation {
	case OperationMetadata:
		metadata := plugin.Metadata()
		if metadata.APIVersion == 0 {
			metadata.APIVersion = ABIVersion
		}
		if metadata.Environment == "" {
			metadata.Environment = PluginEnvironmentServer
		}
		result.Data = metadata
	case OperationInit:
		var event InitEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(Initializer); ok {
				err = handler.Init(context, event)
			}
		}
	case OperationTick:
		var snapshot ServerSnapshot
		snapshot, err = decodeTickSnapshot(input)
		if err == nil {
			if handler, ok := plugin.(TickHandler); ok {
				err = handler.Tick(context, snapshot)
			}
		}
	case OperationChat:
		var event ChatEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ChatHandler); ok {
				err = handler.Chat(context, event)
			}
		}
	case OperationDeath:
		var event DeathEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(DeathHandler); ok {
				err = handler.Death(context, event)
			}
		}
	case OperationSystemCallResult:
		var event SystemCallResult
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(SystemCallResultHandler); ok {
				err = handler.SystemCallResult(context, event)
			}
		}
	case OperationDeinit:
		var event DeinitEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(Deinitializer); ok {
				err = handler.Deinit(context, event)
			}
		}
	case OperationClientTick:
		var event ClientTickEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ClientTickHandler); ok {
				err = handler.ClientTick(context, event)
			}
		}
	default:
		err = fmt.Errorf("sdk: unknown operation %d", operation)
	}

	if err != nil {
		result.Status = "error"
		result.Error = err.Error()
	}
	return nil
}

func currentPlugin() Plugin {
	pluginMu.RLock()
	defer pluginMu.RUnlock()
	if registeredPlugin == nil {
		panic("sdk: plugin was not registered; call sdk.Register from init")
	}
	return registeredPlugin
}

func decode(input []byte, target any) error {
	if len(input) == 0 {
		return errors.New("sdk: operation requires an input payload")
	}
	if err := json.Unmarshal(input, target); err != nil {
		return fmt.Errorf("sdk: decode input: %w", err)
	}
	return nil
}
