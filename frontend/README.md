# BridgeMods frontend

Svelte 5 frontend for publishing and distributing GoMinecraftBridge mods. It consumes the HTTP gateway defined in `../backend/api/project/v1/project.proto` and includes Russian and English localization.

## Development with Bun

```bash
bun install
bun run dev
```

The development server proxies `/v1` to `http://localhost:8080`. Override that target with `VITE_BACKEND_PROXY`; for a production API on another origin, set `VITE_API_BASE_URL`. See `.env.example`.

## Checks

```bash
bun run check
bun run lint
bun test
bun run build
```

## Implemented flows

- registration, login, refresh-token recovery, logout, and profile editing;
- public project search, project details, version listing, and downloads;
- owner workspace: draft creation, slug availability, editing, archive upload, and review submission;
- project-scoped moderation discussion backed by notifications;
- moderator list-detail queue, owner messages, approve/reject decisions, and decision notifications;
- adaptive drawer/rail/bottom navigation, light/dark schemes, RU/EN switching, keyboard tabs, and recoverable loading/error/empty states.

The current backend contract has no RPC for an owner to send a free-form chat reply. The project discussion therefore shows review events and moderator messages, while the owner’s outgoing context is the comment attached to a review submission.

Publication terms, default GPLv3 licensing, moderation rules, and privacy disclosures are documented in [EULA.md](EULA.md). The canonical GPLv3 text is referenced from the parent project as `../LICENSE`; it is not duplicated by this frontend.
