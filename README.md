# GBM

GBM (GoBridgeMinecraft) hosts plugins written in Go in both Minecraft server and
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
- initialization, server/client tick, cancellable chat, player join/disconnect,
  block/entity interactions,
  living-entity damage/death/conversion,
  system-call-result, and deinitialization callbacks;
- entity snapshots and explicit subscriptions to block positions;
- chat broadcast/direct-message actions;
- per-runtime capability negotiation and action-result acknowledgements;
- extensible namespaced system calls;
- capture of Go `stdout`, `stderr`, and the standard `log` package;
- FlatBuffers tick snapshots and bounded binary framebuffer captures (ABI v3),
  while ordinary control-plane messages remain JSON;
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
versions/1.21.1/build/libs/gbm-1.21.1-<version>.jar
versions/26.1.2/build/libs/gbm-26.1.2-<version>.jar
platforms/paper/build/libs/gbm-paper-<version>.jar
```

Run a development client for one target with `./gradlew :mc1211:runClient` or
`./gradlew :mc2612:runClient`.

## Publish to Modrinth

Create the Modrinth project first, then either set its ID/slug in
`gradle.properties` as `modrinth_project_id` or export
`MODRINTH_PROJECT_ID`. Keep the personal access token out of Gradle properties:

```bash
export MODRINTH_TOKEN='<token with CREATE_VERSION scope>'
./gradlew publishModrinth
```

The task publishes separate versions for Fabric/Quilt 1.21.1 and 1.21.11,
Fabric/Quilt 26.1.2, and the shared Paper/Purpur JAR so that Modrinth assigns the
correct loaders and game versions to every artifact. Versions are marked as
beta by default; override that with `MODRINTH_VERSION_TYPE=release` (or `alpha`)
and set `MODRINTH_CHANGELOG` when needed.
The `mc/vX.Y.Z` GitHub release workflow also publishes automatically after its
GitHub Release is created when the repository variable `MODRINTH_PROJECT_ID`
and secret `MODRINTH_TOKEN` are configured.

## Paper and Purpur installation

Copy the shaded Paper JAR to the server's `plugins` directory, start the server
once, and put native Go libraries in:

```text
plugins/GBM/plugins/libmy_plugin.so
```

Use `.dll` on Windows and `.dylib` on macOS. Plugin data is stored separately in
`plugins/GBM/data/<plugin-id>`. Paper/Purpur invokes the same ABI
operations as Fabric: metadata, init, server tick, chat, player join/disconnect,
interaction, death, system-call result, and deinit. Snapshots, chat actions, and
all built-in system calls use the public Bukkit/Paper API rather than Minecraft
internals.

Paper/Purpur dispatches `AfterDamageHandler` from the final uncancelled Bukkit
damage event and uses Paper's Adventure `AsyncChatEvent` for `AllowChatHandler`
and `ChatHandler`. Pre-damage allow/deny and mob-conversion callbacks remain
Fabric-specific and can be detected through `InitEvent.Capabilities`.

An operator or the server console can inspect and manage the runtime with:

```text
/gbm status
/gbm packages
/gbm metadata <plugin-id>
/gbm logs <plugin-id> [count]
/gbm subscribe <plugin-id>
/gbm unsubscribe <plugin-id|all>
/gbm reload <plugin-id>
/gbm rescan
/gbm load <catalog-slug>
```

`/gbm subscribe <plugin-id>` forwards new SDK logs and captured Go
stdout/stderr to that command sender until `/gbm unsubscribe <plugin-id|all>`
is used. Player subscriptions are removed automatically on disconnect; console
subscriptions remain active until explicitly removed or the Paper plugin stops.

The Paper plugin creates `plugins/GBM/config.yml`; set `catalog.backend-url`
there to the public GBM backend. `/gbm load <catalog-slug>` resolves one exact
published slug and shows its name, authors, version, release channel, and
description before presenting clickable `[y]`/`[n]` confirmation buttons. The
confirmation expires after 60 seconds. An accepted package is downloaded with
the existing size and SHA-256 checks and installed atomically under
`plugins/GBM/plugins/<slug>/`. A new package is discovered and initialized
immediately. Reinstalling an existing package writes the new binary but requires
a full server restart before that binary can run.

Paper/Purpur runtime information and controls are available through `/gbm` on
the server. The Fabric client does not request or display server plugin state.

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
config/gbm/plugins/libhello_native.so
```

Use `.dll` on Windows and `.dylib` on macOS. The plugin reports its own ID,
name, version, authors, and config schema through the ABI; no sidecar manifest is
required. Plugins may additionally declare an environment of `server`, `client`,
or `both`; omitted environment metadata remains compatible and means `server`.
Client and `both` plugins installed on a client belong in:

```text
config/gbm/client-plugins/libmy_plugin.so
```

They run independently of the connected server. Client plugin data is kept in
`config/gbm/client-data/<plugin-id>`; server plugin data remains under
`config/gbm/data/<plugin-id>`.

For migration, GBM also scans the former `config/go-minecraft-bridge/plugins`
and `client-plugins` directories. New installations and all newly written data
use `config/gbm`.

The client runtime emits `Init`, `ClientTick`, and `Deinit`, captures plugin
logs, supports local rescan/reload, and permits local chat, retained HUD,
custom retained screens, and framebuffer capture actions.
The HUD action supports text and filled rectangles in GUI-scaled pixels, with
screen anchors and ARGB colors. Server actions, snapshot subscriptions,
and system calls are rejected in a client process, so a client plugin cannot use
the bridge to bypass a remote server's permissions.

`InitEvent.Capabilities` reports the exact features implemented by the current
Fabric client, Fabric server, or Paper host. Every action queued by the current
SDK returns an ID and is acknowledged through the optional
`ActionResultHandler`; built-in system calls continue to report through
`SystemCallResultHandler`.

A client plugin can replace its HUD scene from any callback. It remains visible
until replaced, cleared, or the plugin stops:

```go
ctx.SetHUD(
    sdk.HUDRectangle(8, 8, 120, 18, 0x90000000, sdk.HUDTopLeft),
    sdk.HUDText("Go HUD", 14, 13, 0xffffffff, true, sdk.HUDTopLeft),
)
// ctx.ClearHUD()
```

Individual elements use plugin-local IDs and can be updated, removed, or given
a lifetime without replacing the rest of the scene:

```go
notice := sdk.HUDText("Saved", 8, 8, 0xffffffff, true, sdk.HUDTopRight).
    Named("save-notice").
    Temporary(3 * time.Second)

ctx.RenderHUD(notice)          // create or update by ID
ctx.RemoveHUD("save-notice") // remove immediately
```

Client plugins can compose a custom screen from freely positioned retained
elements. Every element has GUI-scaled coordinates, dimensions and a screen
anchor. Rectangles and text provide custom visuals; invisible hitboxes make any
composition clickable; vanilla buttons, inputs and selects are available when
native widgets are useful. Events are delivered to `ClientScreenEventHandler`:

```go
ctx.OpenScreen(sdk.ClientScreen{
    ID: "custom", Title: "Custom screen",
    Elements: []sdk.ClientScreenElement{
        {
            ID: "panel", Type: sdk.ClientScreenElementRectangle,
            Anchor: sdk.HUDCenter, Width: 300, Height: 180,
            Color: 0xe0181820,
        },
        {
            ID: "caption", Type: sdk.ClientScreenElementText,
            Anchor: sdk.HUDCenter, Y: -50,
            Text: "Rendered from a Go scene", Color: 0xff55ff55,
        },
        {
            ID: "action", Type: sdk.ClientScreenElementHitbox,
            Anchor: sdk.HUDCenter, Y: 40, Width: 120, Height: 20,
        },
    },
})
```

Elements are painted in slice order. Calling `OpenScreen` again with the
same ID replaces the retained scene, so event handlers can implement arbitrary
stateful UI. `Fields` and `Buttons` remain shorthand for a conventional form;
they do not constrain screens built through `Elements`.

`CaptureScreen` requests the current complete framebuffer without a
request ID. The result arrives as top-to-bottom RGBA8 bytes; it is not a PNG,
Base64 value, or pre-decoded QR code:

```go
func (myPlugin) ScreenCaptured(ctx *client.Context, capture sdk.ClientScreenCapture) error {
    pixels := capture.Pixels // valid during this callback
    _ = pixels
    return nil
}
```

The runtime coalesces repeated requests from a plugin while a capture is in
progress. Client screen methods are not exposed by `server.Context`.

Native access and JOML's supported NIO memory path are enabled with:

```text
--enable-native-access=ALL-UNNAMED
-Djoml.nounsafe=true
```

The development run configurations add this automatically. A production server
must add it to its JVM arguments.

After joining a development server, send `!go` in chat. The example responds
directly to the player and requests `minecraft:server.info` through the system
call registry.

## Cloth Config management screen

Install the Cloth Config and Mod Menu versions from the target table, then open
**Mods → GBM → Configure**. The screen shows only local client packages and the
public package catalog. For local packages it provides:

- validation results for native packages found in `plugins` and `mods`;
- plugin metadata, config schema, backend, origin, and lifecycle state;
- the latest retained bridge/SDK/stdout/stderr logs;
- a package rescan that can discover and initialize newly added libraries;
- a logical plugin reload (`Deinit → Init`), including recovery from a disabled state.

Client native plugins are also represented as individual entries in Mod Menu.
When a plugin exposes a writable Go configuration struct, its own **Configure**
button opens a generated Cloth Config screen. Boolean, string, integer, floating
point, primitive-list, and nested struct fields are supported. Saved values are
delivered to the live Go plugin and persisted in
`client-data/<plugin-id>/config.json`.

The **Package catalog** category connects directly to the public backend API.
Set its backend URL, enter a search query, and save; reopen the screen to select
and install a published client package. Catalog-managed packages are stored in
`config/gbm/client-plugins/<project-slug>/`, with the native library normalized
to `<project-slug>.so` (`.dll` or `.dylib` on other platforms). Raw native
libraries and ZIP archives are supported. Downloads are size-limited, verified
against the release SHA-256, and ZIP paths are confined to the package folder.

Each installed package receives a `gbm-package.json` source manifest, while the
catalog settings and installed-project index are persisted in
`config/gbm/repository.json`. Enabling automatic updates checks every package in
that index at client startup and installs newer backend releases. A newly
installed library can be discovered immediately; replacing a library that is
already loaded still takes effect after Minecraft restarts.

A Java-side backend integration can attach a custom Mod Menu update checker to
one of these entries. The checker is run on Mod Menu's worker thread, so its
callback may synchronously request the version endpoint:

```java
GoMinecraftBridgeClient.runtime().modMenu().registerUpdateChecker("my_plugin", () -> {
    BackendVersion version = backend.checkVersion("my_plugin");
    if (!version.updateAvailable()) {
        return null;
    }
    return new NativePluginUpdateInfo(
            version.downloadUrl(),
            UpdateChannel.RELEASE
    );
});
```

Registration automatically enables update checks for that entry and starts a
new Mod Menu check. Call `unregisterUpdateChecker("my_plugin")` on the same
adapter to detach it.

Local client package inspection, logs, rescan, and lifecycle reload do not need
server permission. Cloth Config and Mod Menu are client-only optional
integrations; a dedicated server only needs GBM and Fabric API. The client mod
does not register a remote management channel and can remain installed when
joining any server.

Native libraries cannot be safely unloaded from a running JVM. Rescan can load a
new file, and lifecycle reload can restart an existing plugin, but replacing the
bytes of an already loaded `.so`, `.dll`, or `.dylib` still requires a full JVM
restart.

## Plugin programming model

A Go project imports the common SDK types and exactly one registration package.
The native C exports, panic boundary, output allocation, and `gmb_free`
implementation are linked into the final library automatically:

```go
package main

import (
    "github.com/yawaflua/GoMinecraftBridge/sdk"
    "github.com/yawaflua/GoMinecraftBridge/sdk/server"
)

type myPlugin struct{}

func init() {
    server.Register(sdk.Metadata{
        ID: "my_plugin", Name: "My plugin", Version: "1.0.0",
        License: "MIT", ConfigSchema: cfg,
    }, myPlugin{})
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

It implements only the callbacks it needs. Configuration is an ordinary pointer
to a Go struct; the SDK updates it before invoking `ConfigUpdated`:

```go
type config struct {
    Greeting string `json:"greeting"`
    Enabled  bool   `json:"enabled"`
}

var cfg = &config{Greeting: "Hello from Go", Enabled: true}

func (myPlugin) ConfigUpdated(ctx *server.Context, event sdk.ConfigUpdateEvent) error {
    // cfg already contains the values saved in Cloth Config.
    return nil
}

func (myPlugin) Tick(ctx *server.Context, snapshot sdk.ServerSnapshot) error {
    for _, entity := range snapshot.Entities {
        // Read the immutable snapshot.
        _ = entity
    }
    return nil
}

func (myPlugin) Chat(ctx *server.Context, event sdk.ChatEvent) error {
    ctx.SendMessage(event.PlayerUUID, "Hello from Go")
    return nil
}

```

Client-only plugins use `client.Register`; a single binary with independent
server and client parts uses `dual.Register(metadata, serverPart, clientPart)`.

See [`examples/hello-native/main.go`](examples/hello-native/main.go) for a
complete native entrypoint demonstrating both server and client callbacks.

`InteractionHandler` observes left/right clicks on blocks and entities without
cancelling vanilla behavior. The event includes `Sneaking`, `Hand`, the complete
player snapshot, and either `Block` or `Target`. The example demonstrates
Shift-click detection for signs and player entities.

`PlayerJoinHandler` and `PlayerDisconnectHandler` receive a
`PlayerConnectionEvent` containing the complete player snapshot and timestamp.
They are server-only observational callbacks; returning an error cannot accept
or reject a connection. See [`examples/hello-server/main.go`](examples/hello-server/main.go)
for a server plugin that logs both events and broadcasts join/leave messages.

`AllowChatHandler` runs before the observational `ChatHandler`. Returning
`false` cancels the vanilla chat message; missing handlers and handler errors
are fail-open. The `hello-server` example rejects a selected word and sends a
private explanation to the player.

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
- `minecraft:client.chat.display` via `ctx.DisplayMessage(...)` (client runtime only).
- `minecraft:client.screen.open` and `.close` via the typed screen API;
- `minecraft:client.screen.capture` via `ctx.CaptureScreen()`.

System calls return a result to the plugin's `SystemCallResult` callback. Built-in
calls currently include:

- `sdk.SystemCallServerInfo` (`minecraft:server.info`);
- `sdk.SystemCallPlayerGet` (`minecraft:player.get`);
- `sdk.SystemCallBlockGet` (`minecraft:block.get`);
- `sdk.SystemCallGetEntity` (`minecraft:entity.get`).

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
GBM_TEST_LIBRARY="$PWD/examples/hello-native/dist/libhello_native.so" ./gradlew test
(cd sdk && go test ./...)
```

The code layout is documented in [`docs/architecture.md`](docs/architecture.md),
and the wire-level contract in [`docs/native-abi.md`](docs/native-abi.md).

## Codec and performance

ABI v3 uses FlatBuffers for the high-frequency Java → Go tick snapshot. Metadata,
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
