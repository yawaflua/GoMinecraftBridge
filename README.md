# Go Minecraft Bridge

Go Minecraft Bridge hosts plugins written in Go in both Minecraft server and
client processes. The Minecraft-facing code stays in Java, while plugin logic
receives immutable snapshots/events and returns actions or named system calls.

The shared code is built as two version-specific Fabric artifacts:

| Minecraft | Java | Loader | Fabric API | Cloth Config | Mod Menu |
|---|---:|---:|---:|---:|---:|
| `1.21.1` | 21 | `0.16.14` | `0.116.14+1.21.1` | `15.0.140` | `11.0.3` |
| `26.1.2` | 25 | `0.19.3` | `0.149.1+26.1.2` | `26.1.154` | `18.0.0` |

Architectury Loom drives the portable 1.21.1 target, with a small source
overlay for Minecraft API differences. The unobfuscated 26.1.2 target uses
Fabric Loom because Architectury Loom currently requires a mappings artifact
that this Minecraft distribution does not publish. Architectury portability
still produces one JAR per Minecraft ABI; it does not make one universal JAR.

The current MVP also targets:

- Go 1.24 or newer;
- native Go plugins built with `-buildmode=c-shared`;
- initialization, server/client tick, chat, death, system-call-result, and deinitialization callbacks;
- entity snapshots and explicit subscriptions to block positions;
- chat broadcast/direct-message actions;
- extensible namespaced system calls;
- capture of Go `stdout`, `stderr`, and the standard `log` package;
- FlatBuffers tick snapshots (ABI v2), while control-plane messages remain JSON;
- a Cloth Config management screen exposed through Mod Menu.

The backend interface is deliberately independent from Fabric and native FFI so
that a WASI/WASM backend can implement the same protocol later.

The server runtime is also packaged as one Paper plugin compatible with Paper
and Purpur. Its Bukkit-facing code is compiled during every build against this
API matrix:

| Server | Paper API used for verification | Java runtime |
|---|---|---:|
| Paper/Purpur `1.21.1` | `1.21.1-R0.1-SNAPSHOT` | 21+ |
| Paper/Purpur `1.21.11` | `1.21.11-R0.1-SNAPSHOT` | 21+ |
| Paper/Purpur `26.1` | `26.1.2.build.74-stable` | 25+ |

The Paper API preserves the Bukkit surface used by the bridge, so these targets
share one Java 21 bytecode JAR. Purpur accepts it directly because Purpur is a
Paper-compatible server implementation.

## Build and try the example

Build both Fabric targets:

```bash
./gradlew build
```

The production JARs are written to:

```text
versions/1.21.1/build/libs/go-minecraft-bridge-1.21.1-<version>.jar
versions/26.1.2/build/libs/go-minecraft-bridge-26.1.2-<version>.jar
platforms/paper/build/libs/go-minecraft-bridge-paper-<version>.jar
```

Run a development client for one target with `./gradlew :mc1211:runClient` or
`./gradlew :mc2612:runClient`.

## Paper and Purpur installation

Copy the shaded Paper JAR to the server's `plugins` directory, start the server
once, and put native Go libraries in:

```text
plugins/GoMinecraftBridge/go-plugins/libmy_plugin.so
```

Use `.dll` on Windows and `.dylib` on macOS. Plugin data is stored separately in
`plugins/GoMinecraftBridge/data/<plugin-id>`. Paper/Purpur invokes the same ABI
operations as Fabric: metadata, init, server tick, chat, death, system-call
result, and deinit. Snapshots, chat actions, and all built-in system calls use
the public Bukkit/Paper API rather than Minecraft internals.

An operator or the server console can inspect and manage the runtime with:

```text
/gmb status
/gmb packages
/gmb metadata <plugin-id>
/gmb logs <plugin-id> [count]
/gmb reload <plugin-id>
/gmb rescan
```

On `1.21.1` and `26.1.2`, an operator using the matching Fabric client mod can
also open the existing Cloth Config screen. The Paper plugin exposes the same
`go_minecraft_bridge:admin_request`/`admin_response` management channels, with
package paths, metadata, logs, rescan, and reload withheld from non-OP players.
Large responses are shortened below Paper's plugin-message limit; `/gmb` remains
available for complete server-side output. A separate Fabric client target is
still required before this UI can be used on Minecraft `1.21.11`.

Lifecycle reload does not unload the native library; replacing an already
loaded binary still requires a full server restart. Start Paper/Purpur with
`--enable-native-access=ALL-UNNAMED`, just like the Fabric server.

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
required. Plugins may additionally declare an environment of `server`, `client`,
or `both`; omitted environment metadata remains compatible and means `server`.
Client and `both` plugins installed on a client belong in:

```text
config/go-minecraft-bridge/client-plugins/libmy_plugin.so
```

They run independently of the connected server. Client plugin data is kept in
`config/go-minecraft-bridge/client-data/<plugin-id>`; server plugin data remains
under `config/go-minecraft-bridge/data/<plugin-id>`.

The client runtime emits `Init`, `ClientTick`, and `Deinit`, captures plugin
logs, supports local rescan/reload, and permits only the local
`minecraft:client.chat.display` action. Server actions, snapshot subscriptions,
and system calls are rejected in a client process, so a client plugin cannot use
the bridge to bypass a remote server's permissions.

Native access must be enabled for the unnamed Java module:

```text
--enable-native-access=ALL-UNNAMED
```

The development run configurations add this automatically. A production server
must add it to its JVM arguments.

After joining a development server, send `!go` in chat. The example responds
directly to the player and requests `minecraft:server.info` through the system
call registry.

## Cloth Config management screen

Install the Cloth Config and Mod Menu versions from the target table, then open
**Mods → Go Minecraft Bridge → Configure**. The screen always shows local client
packages and, when supported by the connected server, server packages. It provides:

- validation results for native packages found in `plugins` and `mods`;
- plugin metadata, config schema, backend, origin, and lifecycle state;
- the latest retained bridge/SDK/stdout/stderr logs;
- a package rescan that can discover and initialize newly added libraries;
- a logical plugin reload (`Deinit → Init`), including recovery from a disabled state.

Local client package inspection, logs, rescan, and lifecycle reload do not need
server permission. Server information and controls still require a player from
the server's vanilla OP list.

Management data and actions are returned only to players present in the
server's vanilla OP list. A non-OP response contains no package paths, plugin
metadata, or logs. Cloth Config and Mod Menu are client-only optional
integrations; a dedicated server only needs Go Minecraft Bridge and Fabric API.
The client mod can remain installed when joining an ordinary server without the
bridge: it detects the missing management channel and disables only the remote
package/plugin screen for that connection.

Native libraries cannot be safely unloaded from a running JVM. Rescan can load a
new file, and lifecycle reload can restart an existing plugin, but replacing the
bytes of an already loaded `.so`, `.dll`, or `.dylib` still requires a full JVM
restart.

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

func (myPlugin) ClientTick(ctx *sdk.Context, event sdk.ClientTickEvent) error {
    if event.Connected && event.Tick%200 == 0 {
        ctx.DisplayClientMessage("Client Go runtime is active")
    }
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
- `minecraft:chat.player`;
- `minecraft:client.chat.display` via `ctx.DisplayClientMessage(...)` (client runtime only).

System calls return a result to the plugin's `SystemCallResult` callback. Built-in
calls currently include:

- `sdk.SystemCallServerInfo` (`minecraft:server.info`);
- `sdk.SystemCallPlayerGet` (`minecraft:player.get`);
- `sdk.SystemCallBlockGet` (`minecraft:block.get`);
- `sdk.SystemCallGetEntity` (`minecraft:get_entity`).

Built-in calls are requested through the typed `SystemCallType` API:

```go
ctx.SystemCall(sdk.SystemCallServerInfo, map[string]any{})

runtimeID := snapshot.Entities[0].RuntimeID
ctx.SystemCall(sdk.SystemCallGetEntity, sdk.GetEntityRequest{
    RuntimeID: &runtimeID,
})
```

`SystemCallGetEntity` accepts exactly one selector: `UUID` or `RuntimeID`. It
returns a complete `sdk.EntitySnapshot`, including position, velocity, type,
health, and dimension. The result data is JSON `null` when the selected entity
is no longer loaded.

Calls registered by another mod remain available through the explicitly named
custom API:

```go
ctx.CustomSystemCall("example:claim.owner", map[string]any{})
```

Another Java mod can expose a custom call without changing the bridge:

```java
GoMinecraftBridgeApi.systemCalls().register("example:claim.owner", (context, payload) -> {
    JsonObject result = new JsonObject();
    result.addProperty("owner", "player uuid");
    return result;
});
```

Discovered metadata, including each plugin's config schema, is also available
through `GoMinecraftBridgeApi.plugins()`. Go plugins are intentionally not
injected into Fabric Loader's already-finalized mod list.

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

ABI v2 uses FlatBuffers for the high-frequency Java → Go tick snapshot. Metadata,
initialization, chat/death events, logs, actions, and arbitrary system calls stay
JSON because they are smaller and occur less often. Plugin code still receives a
normal `sdk.ServerSnapshot`; generated FlatBuffers types are internal to the SDK.

On the current development machine (Ryzen 5 1600), the synthetic 1000-entity Go
benchmark measures roughly `0.65–0.69 ms` per FlatBuffers conversion versus
`8.76–8.99 ms` for JSON decode (about 13× faster). Allocated memory drops from
about `451 KB` to `260 KB`. This benchmark isolates Go decoding; actual server
gain depends on entity count, snapshot subscriptions, Java world traversal, FFI
copying, and plugin work.

Run the comparison locally with:

```bash
(cd sdk && go test -bench 'Benchmark(JSON|FlatBuffers)Snapshot1000Entities' -benchmem -run '^$')
```

The source schema is [`schema/tick_snapshot.fbs`](schema/tick_snapshot.fbs), and
generated Java/Go readers are checked in so plugin consumers do not need `flatc`.
