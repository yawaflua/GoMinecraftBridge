# GBM architecture

GBM is split by responsibility so a feature normally changes one layer and one
platform adapter instead of a collection of bootstrap classes.

```text
Fabric/Paper entrypoints
        |
        v
platform runtime and event adapters
        |
        v
native plugin registry ---- protocol records ---- native backend
        |
        +---- package scanner

client runtime
  +---- local plugin manager ---- response/action handler ---- HUD state
  +---- catalog task controller ---- catalog service ---- backend API
  +---- Mod Menu adapter
  +---- Cloth screen sections
```

## Source map

- `catalog`: backend transport, persistent settings/manifests, integrity checks,
  archive installation, and catalog use cases. `GbmCatalogService` is the entry
  point and does not depend on Minecraft UI classes.
- `host`: platform-neutral discovery and lifecycle primitives.
  `NativePackageScanner` finds candidates and `NativePluginRegistry` owns the
  loaded set. Fabric and Paper reuse both.
- `fabric`: Fabric server event wiring. `GoMinecraftBridgeMod` is only the
  loader entry point; `GbmFabricServerRuntime` owns runtime bindings.
- `client/runtime`: the client composition root. `GbmClientRuntime` coordinates
  local plugins, remote administration, catalog jobs, and UI-facing state.
- `client/catalog`: asynchronous search/install/update operations. Minecraft
  screens read snapshots instead of owning worker threads or network calls.
- `client/plugin`: local configuration persistence and interpretation of plugin
  responses/actions.
- `client/ui`: small Cloth Config sections. `ClothManagementScreen` only
  composes these sections.
- `paper`: Bukkit/Paper-specific event, snapshot, action, and messaging adapters.
- `protocol`: transport-only records. Keep game/platform behavior out of this
  package.

## Dependency rules

1. Entrypoints construct a runtime and register callbacks; they contain no
   catalog, discovery, or UI logic.
2. UI reads runtime snapshots and calls explicit runtime commands. It does not
   access files, HTTP, or native libraries directly.
3. Catalog persistence and installation stay independent from Minecraft.
4. Platform adapters may depend on shared `host` and `protocol` code; shared
   code must not import Fabric, Minecraft, or Bukkit types.
5. A native package is discovered once by `NativePluginRegistry`; platform
   managers only decide whether its declared environment is accepted.

## Package installation flow

```text
Cloth action
  -> CatalogTaskController (worker thread)
  -> GbmCatalogService
  -> CatalogApi / download
  -> SHA-256 verification
  -> PackageInstaller (raw library or safe ZIP extraction)
  -> gbm-package.json + repository.json
  -> client plugin rescan
  -> Mod Menu refresh
```

Managed client packages live at
`config/gbm/client-plugins/<project-slug>/<project-slug>.<native-extension>`.
The legacy `config/go-minecraft-bridge` directories are migration search roots;
new state is always written under `config/gbm`.

## Adding a feature

- New native event: add protocol/SDK records, then one callback in each runtime
  that supports it.
- New client action: add its interpretation to `ClientPluginResponseHandler`.
- New catalog operation: add it to `CatalogApi`, implement transport in
  `BackendCatalogClient`, then expose a use case through `GbmCatalogService`.
- New management UI block: add a `client/ui` section and compose it in
  `ClothManagementScreen`.
- New platform: reuse `host`, `protocol`, and the native backend, and implement
  only the platform event/action/snapshot adapters.

Java package names and the public `GoMinecraftBridgeApi` symbol remain stable
for source compatibility. User-facing IDs, commands, directories, channels, and
artifacts use the short name `gbm`.
