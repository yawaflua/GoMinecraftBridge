package sdk

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"runtime/debug"
	"strings"
	"sync"
)

var (
	pluginMu         sync.RWMutex
	registeredPlugin Plugin
)

// RegisterRuntime installs the low-level plugin adapter used by the side-specific SDK packages.
func RegisterRuntime(plugin Plugin) {
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
	context := &Context{client: dispatchRunsOnClient(operation, input)}
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
		} else if metadata.APIVersion != ABIVersion {
			err = fmt.Errorf("sdk: api version must be %d", ABIVersion)
			break
		}
		if metadata.Environment != PluginEnvironmentServer &&
			metadata.Environment != PluginEnvironmentClient &&
			metadata.Environment != PluginEnvironmentBoth {
			err = errors.New("sdk: plugin environment must be server, client, or both")
			break
		}
		metadata.ConfigWritable = configWritable(plugin, metadata.ConfigSchema)
		metadata.ConfigEnums = configEnums(metadata.ConfigSchema)
		result.Data = metadata
	case OperationInit:
		var event InitEvent
		err = decode(input, &event)
		if err == nil {
			context.client = event.RuntimeEnvironment == PluginEnvironmentClient
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
	case OperationClientKey:
		var event ClientKeyEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ClientKeyHandler); ok {
				err = handler.ClientKey(context, event)
			}
		}
	case OperationClientChat:
		var event ClientChatEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ClientChatHandler); ok {
				err = handler.ClientChat(context, event)
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
			if support, ok := plugin.(ConfigUpdateSupport); ok {
				handlesUpdate = support.HandlesConfigUpdates(context.RuntimeEnvironment())
			}
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
	case OperationClientScreenEvent:
		var event ClientScreenEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ClientScreenEventHandler); ok {
				err = handler.ClientScreenEvent(context, event)
			}
		}
	case OperationClientScreenCapture:
		var capture ClientScreenCapture
		capture, err = decodeClientScreenCapture(input)
		if err == nil {
			if handler, ok := plugin.(ClientScreenCaptureHandler); ok {
				err = handler.ClientScreenCaptured(context, capture)
			}
		}
	case OperationInteraction:
		var event InteractionEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(InteractionHandler); ok {
				err = handler.Interaction(context, event)
			}
		}
	case OperationActionResult:
		var event ActionResult
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(ActionResultHandler); ok {
				err = handler.ActionResult(context, event)
			}
		}
	case OperationPlayerJoin:
		var event PlayerConnectionEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(PlayerJoinHandler); ok {
				err = handler.PlayerJoin(context, event)
			}
		}
	case OperationPlayerDisconnect:
		var event PlayerConnectionEvent
		err = decode(input, &event)
		if err == nil {
			if handler, ok := plugin.(PlayerDisconnectHandler); ok {
				err = handler.PlayerDisconnect(context, event)
			}
		}
	case OperationAllowChat:
		var event ChatEvent
		err = decode(input, &event)
		if err == nil {
			allowed := true
			if handler, ok := plugin.(AllowChatHandler); ok {
				allowed, err = handler.AllowChat(context, event)
			}
			if err == nil {
				result.Data = allowed
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

func dispatchRunsOnClient(operation int, input []byte) bool {
	if operation == OperationClientTick || operation == OperationClientKey || operation == OperationClientChat || operation == OperationClientScreenEvent || operation == OperationClientScreenCapture {
		return true
	}
	var scope struct {
		RuntimeEnvironment PluginEnvironment `json:"runtimeEnvironment"`
	}
	return json.Unmarshal(input, &scope) == nil && scope.RuntimeEnvironment == PluginEnvironmentClient
}

const (
	clientCaptureHeaderSize = 24
	clientCaptureMaxPixels  = 16_000_000
	clientCaptureMaxBytes   = 64 * 1024 * 1024
)

func decodeClientScreenCapture(input []byte) (ClientScreenCapture, error) {
	if len(input) < clientCaptureHeaderSize || string(input[:4]) != "GMBC" {
		return ClientScreenCapture{}, errors.New("sdk: invalid client screen capture header")
	}
	if input[4] != 1 || input[5] != 1 {
		return ClientScreenCapture{}, errors.New("sdk: unsupported client screen capture format")
	}
	width := uint64(binary.LittleEndian.Uint32(input[8:12]))
	height := uint64(binary.LittleEndian.Uint32(input[12:16]))
	stride := uint64(binary.LittleEndian.Uint32(input[16:20]))
	payloadLength := uint64(binary.LittleEndian.Uint32(input[20:24]))
	if width == 0 || height == 0 || width*height > clientCaptureMaxPixels || stride != width*4 {
		return ClientScreenCapture{}, errors.New("sdk: invalid client screen capture dimensions")
	}
	expected := stride * height
	if expected != payloadLength || payloadLength > clientCaptureMaxBytes || payloadLength != uint64(len(input)-clientCaptureHeaderSize) {
		return ClientScreenCapture{}, errors.New("sdk: invalid client screen capture payload length")
	}
	return ClientScreenCapture{
		Width: int(width), Height: int(height), Stride: int(stride),
		Format: ClientPixelFormatRGBA8, Pixels: input[clientCaptureHeaderSize:],
	}, nil
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
	if support, ok := plugin.(ConfigUpdateSupport); ok {
		if support.HandlesConfigUpdates(PluginEnvironmentServer) ||
			support.HandlesConfigUpdates(PluginEnvironmentClient) {
			return target != nil
		}
	} else if _, ok := plugin.(ConfigUpdateHandler); ok {
		return target != nil
	}
	if target == nil {
		return false
	}
	value := reflect.ValueOf(target)
	return value.Kind() == reflect.Pointer && !value.IsNil()
}

func configEnums(target any) map[string][]any {
	value := reflect.ValueOf(target)
	if !value.IsValid() {
		return nil
	}
	for value.Kind() == reflect.Pointer {
		if value.IsNil() {
			return nil
		}
		value = value.Elem()
	}
	if value.Kind() != reflect.Struct {
		return nil
	}
	result := make(map[string][]any)
	collectConfigEnums(value.Type(), "", result)
	if len(result) == 0 {
		return nil
	}
	return result
}

func collectConfigEnums(valueType reflect.Type, prefix string, result map[string][]any) {
	for index := 0; index < valueType.NumField(); index++ {
		field := valueType.Field(index)
		if field.PkgPath != "" {
			continue
		}
		name := field.Name
		if jsonName := field.Tag.Get("json"); jsonName != "" {
			name = jsonName
			if comma := strings.IndexByte(name, ','); comma >= 0 {
				name = name[:comma]
			}
			if name == "-" {
				continue
			}
		}
		path := name
		if prefix != "" {
			path = prefix + "." + name
		}
		if raw := field.Tag.Get("gbm"); raw != "" {
			var values []any
			if json.Unmarshal([]byte(raw), &values) == nil && len(values) > 0 {
				result[path] = values
			}
		}
		nested := field.Type
		for nested.Kind() == reflect.Pointer {
			nested = nested.Elem()
		}
		if nested.Kind() == reflect.Struct {
			collectConfigEnums(nested, path, result)
		}
	}
}

func currentPlugin() Plugin {
	pluginMu.RLock()
	defer pluginMu.RUnlock()
	if registeredPlugin == nil {
		panic("sdk: plugin was not registered; call server.Register, client.Register, or dual.Register from init")
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
