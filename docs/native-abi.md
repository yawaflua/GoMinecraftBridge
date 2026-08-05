# Native ABI v3

Every native plugin is a Go `main` package built with `-buildmode=c-shared` and
exports three C symbols:

```c
int32_t gmb_abi_version(void);

int32_t gmb_call(
    int32_t operation,
    const uint8_t *input,
    uint64_t input_length,
    uint8_t **output,
    uint64_t *output_length
);

void gmb_free(void *pointer);
```

Plugin authors do not implement these symbols. Importing the Go SDK includes
their native implementation in the final `c-shared` library.

`gmb_call` receives an operation-specific byte buffer and returns a buffer
allocated by the plugin. The host copies the buffer and always returns it through
`gmb_free`. A zero C status means the transport succeeded. Go callback errors and
panics are represented in the JSON response rather than the C status.

## Operations

| Code | Name | Input |
|---:|---|---|
| 1 | metadata | empty |
| 2 | init | `InitEvent` |
| 3 | tick | FlatBuffers `ServerSnapshot` (`GMBS`) |
| 4 | chat | `ChatEvent` |
| 5 | death | `DeathEvent` |
| 6 | system call result | `SystemCallResult` |
| 7 | deinit | `DeinitEvent` |
| 8 | client tick | `ClientTickEvent` |
| 9 | config update | `ConfigUpdateEvent` |
| 10 | allow damage | `AllowDamageEvent`; boolean decision in response `data` |
| 11 | after damage | `AfterDamageEvent` |
| 12 | allow death | `AllowDeathEvent`; boolean decision in response `data` |
| 13 | mob conversion | `MobConversionEvent` |
| 14 | client screen event | `ClientScreenEvent` JSON |
| 15 | client screen capture | binary `GMBC` framebuffer |
| 16 | interaction | `InteractionEvent` |
| 17 | action result | `ActionResult` |
| 18 | player join | `PlayerConnectionEvent` |
| 19 | player disconnect | `PlayerConnectionEvent` |
| 20 | allow chat | `ChatEvent`; boolean decision in response `data` |

Operations 10, 12, and 20 are synchronous decisions. Damage and death decisions
are Fabric-only; chat decisions are supported by Fabric and Paper. A plugin that
does not implement the matching handler returns `true` automatically. Handler
errors, panics, malformed decisions, and transport failures are also fail-open;
an event is denied when at least one successfully invoked plugin returns `false`.

Operation 11 is emitted by both Fabric and Paper. Paper reports Bukkit's base
and final damage at `MONITOR` priority; `blocked` is true when positive base
damage was reduced to zero. Fabric additionally exposes the pre-damage decision
and mob-conversion events.

Operation 16 observes `use_block`, `attack_block`, `use_entity`, and
`attack_entity` without cancelling the vanilla action. It includes the hand,
sneaking state, player snapshot, clicked block or entity snapshot, block face,
and hit coordinates when the platform provides them. Fabric emits it in both
server and client runtimes; Paper emits authoritative server interactions.

Operation 17 acknowledges an action that contains an `id`. Its JSON input
contains the original `id` and `type`, a `success` boolean, and an optional
`error`. Action-result and system-call-result callbacks may emit more work; the
host limits the combined callback chain to 32 levels.

Operations 18 and 19 are server-only notifications. Both carry the player's
complete `EntitySnapshot` and an event timestamp. Join is emitted after the
connection has been established; disconnect is emitted while the platform can
still create the departing player's snapshot. Handler errors are reported like
other observational events and do not affect the connection lifecycle.

Operation 20 runs before operation 4. If every decision allows the message,
the platform broadcasts it normally and then emits the observational chat
callback. If any plugin returns `false`, the platform cancels the message and
does not emit operation 4 for it.

Every successful transport response uses this envelope:

```json
{
  "status": "ok",
  "error": "",
  "stack": "",
  "data": null,
  "logs": [],
  "actions": [
    {
      "id": "action-1",
      "type": "minecraft:chat.player",
      "payload": {"playerUuid": "...", "message": "hello"}
    }
  ],
  "systemCalls": [],
  "snapshot": null
}
```

`status` is one of `ok`, `error`, or `panic`. The host disables a plugin after a
panic. An ordinary handler error is logged but does not disable an already-running
plugin.

`InitEvent.capabilities` is the runtime's immutable list of supported events,
actions, and system calls. A `both` plugin should use `InitEvent.Supports`
before relying on optional or platform-specific functionality. Fabric server,
Fabric client, and Paper intentionally advertise different lists.

All inputs except operations 3 and 15 are UTF-8 JSON. Server tick input follows
[`schema/tick_snapshot.fbs`](../schema/tick_snapshot.fbs) and includes the
FlatBuffers file identifier `GMBS`. Operation 15 starts with a 24-byte
little-endian header: `GMBC`, version byte `1`, format byte `1` (`RGBA8`), two
reserved bytes, then `uint32` width, height, row stride, and payload length.
Tightly packed top-to-bottom RGBA bytes follow the header. Frames are limited
to 16 million pixels and 64 MiB. Responses remain UTF-8 JSON because action,
log, subscription, and system-call batches are normally small. A native library
compiled against any earlier ABI is rejected by an ABI v3 host before
initialization.

All memory ownership stays on its allocating side. Java objects, Go pointers,
and Go structs never cross the boundary.

## Native plugin configuration

A client or `both` plugin can expose a pointer to a JSON-serializable Go struct
through `Metadata.ConfigSchema`. The SDK marks pointer-backed values as writable.
GBM then creates a configure button for the emulated native mod
entry, converts its fields to Cloth Config entries, and sends the complete value
through operation 9 whenever the screen is saved. The SDK unmarshals the value
into the original pointer before calling the optional `ConfigUpdated` callback.

Accepted values are persisted under
`config/gbm/client-data/<plugin-id>/config.json` and restored
before `Init`. A map containing JSON Schema can still be exposed, but it must be
paired with `ConfigUpdateHandler` because a schema map is not itself the target
configuration object.

## Plugin environment metadata

The metadata response contains an `environment` field with one of `server`,
`client`, or `both`. The `server`, `client`, and `dual` SDK registration
packages set it automatically. Server hosts execute `server` and `both`
plugins; client hosts execute `client` and `both` plugins.

Client libraries are loaded from
`config/gbm/client-plugins`; their persistent data is isolated under
`config/gbm/client-data/<plugin-id>`. They start even when the connected server
does not have GBM installed. The client
tick JSON contains the tick number, connection state, remote address, local
player UUID/name, and current dimension; world-dependent strings are absent
outside a world.

The client runtime accepts local chat, retained HUD, custom retained screens,
and framebuffer-capture actions. `client.Context.OpenScreen` accepts an ordered
scene of anchored text, rectangles, hitboxes and native input widgets, then
reports interactions through operation 14. `client.Context.CaptureScreen`
does not return synchronously: the runtime coalesces pending requests, captures
one framebuffer, and invokes operation 15 with RGBA8 bytes. The Go slice is valid
only during `ScreenCaptured`.

Server actions, snapshot subscriptions, and system calls are rejected locally
and are never forwarded to the connected server. `InitEvent.runtimeEnvironment`
tells the dual adapter which plugin part to invoke (`server` or `client`).

The Paper/Purpur host implements the server side of the same ABI through the
`sdk/server` package. Native packages are discovered under
`plugins/GBM/plugins`, and receive `runtimeEnvironment=server`.
The public Bukkit/Paper API supplies entity/block snapshots, actions, and the
built-in Minecraft system calls, so native plugin binaries can be moved between
Fabric, Paper, and Purpur servers without recompilation when the operating
system and CPU architecture match.
