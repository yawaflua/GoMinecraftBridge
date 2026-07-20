# Native ABI v1

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

`gmb_call` receives UTF-8 JSON and returns a buffer allocated by the plugin. The
host copies the buffer and always returns it through `gmb_free`. A zero C status
means the transport succeeded. Go callback errors and panics are represented in
the JSON response rather than the C status.

## Operations

| Code | Name | Input |
|---:|---|---|
| 1 | metadata | empty |
| 2 | init | `InitEvent` |
| 3 | tick | `ServerSnapshot` |
| 4 | chat | `ChatEvent` |
| 5 | death | `DeathEvent` |
| 6 | system call result | `SystemCallResult` |
| 7 | deinit | `DeinitEvent` |

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

All memory ownership stays on its allocating side. Java objects, Go pointers,
and Go structs never cross the boundary. JSON is the v1 transport for clarity;
the same ABI can carry a versioned binary codec in a later protocol revision.
