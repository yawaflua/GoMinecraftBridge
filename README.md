# Go Minecraft Bridge

Go Minecraft Bridge is a Fabric server-side host for plugins written in Go. The
Minecraft-facing code stays in Java, while plugin logic receives immutable tick
snapshots and returns actions or named system calls.

The current MVP targets:

- Minecraft `26.1.2`, Fabric Loader `0.19.3`, Fabric API `0.149.1+26.1.2`;
- Java 25 and Go 1.24 or newer;
- native Go plugins built with `-buildmode=c-shared`;
- initialization, tick, chat, death, system-call-result, and deinitialization callbacks;
- entity snapshots and explicit subscriptions to block positions;
- chat broadcast/direct-message actions;
- extensible namespaced system calls;
- capture of Go `stdout`, `stderr`, and the standard `log` package.

The backend interface is deliberately independent from Fabric and native FFI so
that a WASI/WASM backend can implement the same protocol later.

## Build and try the example

Build the Fabric mod:

```bash
./gradlew build
```

Build the example Go plugin:

```bash
./examples/hello-native/build.sh
```

Copy the resulting library to either location:

```text
mods/libhello_native.so
config/go-minecraft-bridge/plugins/libhello_native.so
```

Use `.dll` on Windows and `.dylib` on macOS. The plugin reports its own ID,
name, version, authors, and config schema through the ABI; no sidecar manifest is
required.

Native access must be enabled for the unnamed Java module:

```text
--enable-native-access=ALL-UNNAMED
```

The development run configurations add this automatically. A production server
must add it to its JVM arguments.

After joining a development server, send `!go` in chat. The example responds
directly to the player and requests `minecraft:server.info` through the system
call registry.

## Plugin programming model

A Go project only imports the SDK and registers one value. The native C exports,
panic boundary, output allocation, and `gmb_free` implementation live inside the
SDK and are linked into the final library automatically:

```go
package main

import "github.com/yawaflua/GoMinecraftBridge/sdk"

type myPlugin struct{}

func init() {
    sdk.Register(myPlugin{})
}

func main() {}
```

Until the SDK is published as a tagged Go module, reference the local checkout:

```go
require github.com/yawaflua/GoMinecraftBridge/sdk v0.0.0

replace github.com/yawaflua/GoMinecraftBridge/sdk => /path/to/GoMinecraftBridge/sdk
```

Build it normally:

```bash
go build -buildmode=c-shared -o dist/my_plugin.so .
```

It implements only the callbacks it needs:

```go
func (myPlugin) Tick(ctx *sdk.Context, snapshot sdk.ServerSnapshot) error {
    for _, entity := range snapshot.Entities {
        // Read the immutable snapshot.
        _ = entity
    }
    return nil
}

func (myPlugin) Chat(ctx *sdk.Context, event sdk.ChatEvent) error {
    ctx.SendMessage(event.PlayerUUID, "Hello from Go")
    return nil
}
```

See [`examples/hello-native/main.go`](examples/hello-native/main.go) for a
complete native entrypoint and every currently supported callback.

### Snapshots

Entity snapshots are enabled by default. Blocks are opt-in because walking all
loaded blocks every tick would be prohibitively expensive:

```go
ctx.SubscribeSnapshot(true,
    sdk.BlockReference{
        Dimension: "minecraft:overworld",
        X: 0,
        Y: 64,
        Z: 0,
    },
)
```

The subscription returned by a callback applies to subsequent ticks for that
plugin.

### Actions and system calls

Actions are fire-and-forget operations implemented by the bridge:

- `minecraft:chat.broadcast`;
- `minecraft:chat.player`.

System calls return a result to the plugin's `SystemCallResult` callback. Built-in
calls currently include:

- `minecraft:server.info`;
- `minecraft:player.get`;
- `minecraft:block.get`.

Another Java mod can expose a custom call without changing the bridge:

```java
GoMinecraftBridgeApi.systemCalls().register("example:claim.owner", (context, payload) -> {
    JsonObject result = new JsonObject();
    result.addProperty("owner", "player uuid");
    return result;
});
```

Discovered metadata, including each plugin's config schema, is available through
`GoMinecraftBridgeApi.plugins()`. This is the integration point for a future
Mod Menu/configuration screen; Go plugins are intentionally not injected into
Fabric Loader's already-finalized mod list.

## Failure model

The SDK converts callback errors into `status=error` and recovers ordinary Go
panics at the ABI boundary. A panic disables that plugin logically. Native Go
libraries remain loaded until JVM shutdown because unloading a live Go runtime is
unsafe.

Native memory corruption, a C/Go runtime fatal error, or a segmentation fault can
still terminate Minecraft. The planned WASM backend is the isolated option for
untrusted plugins.

## Verification

Run all Java and Go tests, including the real Java-to-Go FFI test:

```bash
./examples/hello-native/build.sh
GMB_TEST_LIBRARY="$PWD/examples/hello-native/dist/libhello_native.so" ./gradlew test
(cd sdk && go test ./...)
```

The wire-level contract is documented in [`docs/native-abi.md`](docs/native-abi.md).

## Codec and performance

Protocol v1 deliberately uses JSON because it makes the ABI inspectable while
the schema is still changing. JSON is not intended to remain the hot-path codec:
it formats every number as text and allocates while decoding large snapshots.

The planned split is:

- JSON for metadata, configuration, logs, and arbitrary custom system calls;
- a generated binary schema for tick snapshots and action batches;
- codec negotiation through the plugin metadata, without changing the three C
  ABI functions.

FlatBuffers is the current preferred snapshot codec because Go can read the
snapshot directly from the transferred buffer. Protocol Buffers remains a good
alternative if schema evolution and tooling prove more important than avoiding
decode allocations.
