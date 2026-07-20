# Native ABI v2

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

Every successful transport response uses this envelope:

```json
{
  "status": "ok",
  "error": "",
  "stack": "",
  "data": null,
  "logs": [],
  "actions": [],
  "systemCalls": [],
  "snapshot": null
}
```

`status` is one of `ok`, `error`, or `panic`. The host disables a plugin after a
panic. An ordinary handler error is logged but does not disable an already-running
plugin.

All inputs except operation 3 are UTF-8 JSON. Server tick input follows
[`schema/tick_snapshot.fbs`](../schema/tick_snapshot.fbs) and includes the
FlatBuffers file identifier `GMBS`. Responses remain UTF-8 JSON because action,
log, subscription, and system-call batches are normally small. A native library
compiled against ABI v1 is rejected by an ABI v2 host before initialization.

All memory ownership stays on its allocating side. Java objects, Go pointers,
and Go structs never cross the boundary.

## Plugin environment metadata

The metadata response may contain an `environment` field with one of `server`,
`client`, or `both`. Server hosts execute `server` and `both` plugins; client
hosts execute `client` and `both` plugins. Metadata without this field predates
the declaration and is treated as `server`, so adding it does not change ABI v2.

Client libraries are loaded from
`config/go-minecraft-bridge/client-plugins`; their persistent data is isolated
under `config/go-minecraft-bridge/client-data/<plugin-id>`. They start even when
the connected server does not have Go Minecraft Bridge installed. The client
tick JSON contains the tick number, connection state, remote address, local
player UUID/name, and current dimension; world-dependent strings are absent
outside a world.

The client runtime accepts only the `minecraft:client.chat.display` action,
queued by `Context.DisplayClientMessage`. Server actions, snapshot subscriptions,
and system calls are rejected locally and are never forwarded to the connected
server. `InitEvent.runtimeEnvironment` tells a `both` plugin which host invoked
it (`server` or `client`).

The Paper/Purpur host implements the server side of the same ABI without a
separate Go SDK. Native packages are discovered under
`plugins/GoMinecraftBridge/go-plugins`, and receive `runtimeEnvironment=server`.
The public Bukkit/Paper API supplies entity/block snapshots, actions, and the
built-in Minecraft system calls, so native plugin binaries can be moved between
Fabric, Paper, and Purpur servers without recompilation when the operating
system and CPU architecture match.
