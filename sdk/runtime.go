package sdk

import (
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
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
		metadata.ConfigWritable = configWritable(plugin, metadata.ConfigSchema)
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
	case OperationConfigUpdate:
		var event ConfigUpdateEvent
		err = decode(input, &event)
		if err == nil && len(event.Config) == 0 {
			err = errors.New("sdk: config update requires a config value")
		}
		if err == nil {
			metadata := plugin.Metadata()
			handler, handlesUpdate := plugin.(ConfigUpdateHandler)
			previous, _ := json.Marshal(metadata.ConfigSchema)
			updateErr := updateConfigTarget(metadata.ConfigSchema, event.Config)
			automaticallyUpdated := updateErr == nil
			if updateErr != nil {
				if !handlesUpdate {
					err = updateErr
				}
			} else if metadata.ConfigSchema == nil {
				err = errors.New("sdk: plugin does not expose a configuration")
			}
			if err == nil && handlesUpdate {
				err = handler.ConfigUpdated(context, event)
				if err != nil && automaticallyUpdated && len(previous) != 0 {
					_ = json.Unmarshal(previous, metadata.ConfigSchema)
				}
			}
			if err == nil {
				if automaticallyUpdated {
					result.Data = plugin.Metadata().ConfigSchema
				} else {
					result.Data = event.Config
				}
			}
		}
	case OperationAllowDamage:
		var event AllowDamageEvent
		err = decode(input, &event)
		if err == nil {
			allowed := true
			if handler, ok := plugin.(AllowDamageHandler); ok {
				allowed, err = handler.AllowDamage(context, event)
			}
			if err == nil {
				result.Data = allowed
			}
		}
	case OperationAfterDamage:
		var event AfterDamageEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(AfterDamageHandler); ok {
				err = handler.AfterDamage(context, event)
			}
		}
	case OperationAllowDeath:
		var event AllowDeathEvent
		err = decode(input, &event)
		if err == nil {
			allowed := true
			if handler, ok := plugin.(AllowDeathHandler); ok {
				allowed, err = handler.AllowDeath(context, event)
			}
			if err == nil {
				result.Data = allowed
			}
		}
	case OperationMobConversion:
		var event MobConversionEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(MobConversionHandler); ok {
				err = handler.MobConversion(context, event)
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

func updateConfigTarget(target any, config json.RawMessage) error {
	if target == nil {
		return errors.New("sdk: plugin does not expose a configuration")
	}
	if err := json.Unmarshal(config, target); err != nil {
		return fmt.Errorf("sdk: update config: %w; ConfigSchema must be a non-nil pointer or the plugin must implement ConfigUpdateHandler", err)
	}
	return nil
}

func configWritable(plugin Plugin, target any) bool {
	if _, ok := plugin.(ConfigUpdateHandler); ok {
		return target != nil
	}
	if target == nil {
		return false
	}
	value := reflect.ValueOf(target)
	return value.Kind() == reflect.Pointer && !value.IsNil()
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
