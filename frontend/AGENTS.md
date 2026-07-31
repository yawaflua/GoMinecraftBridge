# AGENTS.md

## Scope and authority

This file applies to the entire `frontend/` directory. It is the working handbook for any model or engineer making changes here.

Use the following sources in this order:

1. The current user request.
2. This file.
3. `CONSTITUTION.md` for detailed Material 3 and product-design rules.
4. `PROMPT.md` for the original design brief and supporting patterns.
5. The implementation and the backend protocol contract.

When documentation and running code disagree, inspect the backend protocol and implementation before deciding. Do not invent endpoints, fields, permissions, or lifecycle transitions.

## Your role

Act as a senior product engineer with strong frontend, product-design, accessibility, and API-integration judgment. Own the requested change from discovery through implementation and verification.

You are expected to:

- understand the user goal and the resulting state before editing;
- preserve existing working behavior and user data;
- derive answers from the repository instead of asking avoidable questions;
- implement complete flows, including loading, empty, success, error, retry, and permission states;
- keep the interface usable with keyboard, touch, narrow screens, dark mode, and both supported languages;
- verify changes with Bun tooling and, for meaningful visual changes, a real browser render;
- report API gaps honestly instead of hiding them behind fake UI.

Do not limit the result to a mockup, recommendation, or static visual when the request calls for working behavior.

## Product identity and purpose

The product is **GBM**, hosted at `https://gbm.ywfl.dev`.

GBM is a publication and distribution platform for GoMinecraftBridge mods. GoMinecraftBridge injects Go code into Minecraft through JNI. The product is conceptually similar to Modrinth, but its domain model, compatibility metadata, moderation workflow, and backend contract are specific to this repository.

The primary product loop is:

1. A user registers or signs in.
2. The user creates a draft project.
3. The user uploads at least one immutable version archive with compatibility metadata, README, and changelog.
4. The user submits the project for moderation.
5. Moderators review the project and communicate through project-scoped notifications shown in the project's Discussion tab.
6. A moderator approves and publishes the project or requests changes.
7. After publication, the owner may edit project metadata and publish additional versions.
8. Visitors discover, inspect, and download published projects.

The product must feel like a reliable technical marketplace, not a generic marketing site or decorative dashboard.

## Technology and commands

The frontend stack is:

- Svelte 5;
- TypeScript;
- Vite;
- Bun as the only package manager and JavaScript runtime used by project commands;
- plain CSS with semantic Material 3 tokens;
- `marked` for Markdown parsing;
- `DOMPurify` for sanitizing rendered Markdown.

Do not introduce Node-specific scripts or use `npm`, `npx`, `yarn`, or `pnpm`. Use Bun:

```bash
bun install
bun run dev
bun run check
bun run lint
bun test
bun run build
```

Before handing off a code change, run at minimum:

```bash
bun run check
bun run lint
bun test
bun run build
```

All four must pass. Do not treat a successful build as a substitute for type checking, linting, or tests.

## Repository map

- `src/App.svelte`: application boot, route selection, and route-level access control.
- `src/pages/`: route-level product screens.
- `src/lib/api.ts`: the single frontend API boundary, authentication retry behavior, and download URL construction.
- `src/lib/session.ts`: session hydration and shared authentication state.
- `src/lib/router.ts`: lightweight client-side navigation.
- `src/lib/i18n.ts`: Russian and English localization.
- `src/lib/Markdown.svelte`: sanitized Markdown rendering.
- `src/lib/Shell.svelte`: adaptive application navigation and global controls.
- `src/lib/Icon.svelte`: the local icon family. Extend this instead of adding an icon package.
- `src/types.ts`: frontend representations of protocol entities and enums.
- `src/utils.ts`: shared presentation and domain helpers.
- `src/styles.css`: global semantic tokens, components, responsive layout, and themes.
- `src/utils.test.ts`: Bun unit tests for shared behavior.
- `EULA.md`: publication terms, content rules, licensing defaults, and privacy disclosure.
- `.env.example`: supported public-site and API configuration.
- `../backend/api/project/v1/project.proto`: authoritative backend API contract.
- `../backend/`: backend implementation and generated gateway files. Read it when protocol behavior is unclear.
- `../LICENSE`: expected canonical GPLv3 license text. Do not duplicate it inside the frontend.

## Routing and access model

The current application routes are:

- `/`: public discovery and search.
- `/project/:slug`: public project page.
- `/auth`: registration and sign-in.
- `/projects`: authenticated owner's project list.
- `/new-project`: authenticated project creation.
- `/projects/:id`: authenticated owner view and settings.
- `/projects/:id/release`: authenticated version upload.
- `/notifications`: authenticated notification center.
- `/profile`: authenticated profile management.
- `/moderation`: moderator or administrator review workspace.

Route guards are intentionally simple and live in `src/App.svelte`. Preserve a clear distinction between public slug routes and authenticated ID routes. If public URL structure changes, coordinate links, routing, canonical URLs, and redirects rather than changing one occurrence.

## Backend contract

Treat `../backend/api/project/v1/project.proto` as the primary contract. Confirm behavior in the Go backend when field semantics, authorization, validation, or HTTP transcoding are unclear. Do not edit generated protobuf or gateway files by hand.

The frontend currently calls the JSON HTTP gateway under `/v1`. In development, Vite proxies `/v1` to `VITE_BACKEND_PROXY`, which defaults to `http://localhost:8080`. In production, same-origin `/v1` is preferred; set `VITE_API_BASE_URL` only when the API is hosted on another origin.

Environment variables:

- `VITE_SITE_URL`: public product URL; defaults to `https://gbm.ywfl.dev`.
- `VITE_BACKEND_PROXY`: development-only Vite proxy target.
- `VITE_API_BASE_URL`: optional production API origin.

Keep all request construction and response error normalization in `src/lib/api.ts`. Pages should call the typed API wrapper rather than using ad hoc `fetch` calls.

### API conventions

- Protocol JSON fields are represented as snake_case in frontend entities.
- Gateway query parameters may use lower camel case, such as `pageSize`, `pageToken`, and `unreadOnly`.
- Partial updates must send `updateMask.paths` values that match the fields being changed.
- Encode every path segment derived from data with `encodeURIComponent`.
- Preserve pagination tokens even when a screen currently requests a large first page.
- Surface backend error messages through `ApiError`, while providing a localized fallback for network and malformed-response failures.
- Never silently convert a failed mutation into a success state.
- Do not fabricate client-side authorization. The UI may hide unavailable actions, but the backend remains authoritative.

### Authentication

Sessions contain the user plus access and refresh tokens and are stored under `gbm.session`. Legacy `bridgemods.session` reads exist only to migrate old local state; do not use the legacy name for new state.

The API wrapper:

- attaches the bearer access token;
- performs one refresh attempt after a `401` response;
- updates shared session state after refresh;
- clears the local session when refresh fails;
- avoids duplicate concurrent refresh requests.

Preserve these properties when changing authentication. Do not log tokens, passwords, archive contents, or personal authentication data.

### Project lifecycle

Project states are:

- `DRAFT`: editable, not publicly listed, and not ready for review until a version exists;
- `PENDING_REVIEW`: awaiting moderation;
- `PUBLISHED`: available in public discovery and downloads;
- `REJECTED`: owner can address feedback and resubmit;
- `BANNED`: publication and update actions are restricted;
- `UNSPECIFIED`: defensive fallback only.

Do not collapse these into a boolean published state. Every screen must use the real lifecycle for available actions, status copy, and recovery guidance.

Versions include an immutable archive and mutable metadata according to the backend contract. A version carries a release tag, README, changelog, size, checksum, authors, licenses, ABI version, API version, and runtime environment. Preserve the backend upload limit and validation behavior; the current UI limits archives to 64 MB.

### Environment semantics

`PLUGIN_ENVIRONMENT_BOTH` means the mod can run in either environment: client or server. It does not mean that the client and server must both install it at the same time.

Compatibility filtering must follow these rules:

- a Client filter matches `CLIENT` and `BOTH`;
- a Server filter matches `SERVER` and `BOTH`;
- a Client or server filter matches `BOTH` specifically;
- when environment and API filters are both active, both conditions must match the same project version.

The backend `SearchProjects` request currently has no environment or API-version fields. The discovery page therefore searches projects first, loads their versions, and filters the current result set client-side. Keep this limitation visible in implementation decisions. Prefer adding a real backend filter contract over pretending client filtering covers all paginated projects.

### Moderation and discussion

Moderators and administrators may access the review workspace. Review decisions and moderator messages are delivered through project-scoped notifications and displayed as a chronological Discussion tab inside the owner's project view.

The current backend has no RPC for arbitrary owner chat replies. The owner can provide context in the review-submission comment, but the UI must not display a nonfunctional reply composer. If bidirectional chat is requested, update the backend contract and authorization model first.

## Markdown

README, version changelog, review comments, moderation discussion messages, and related notification content support GitHub-flavored Markdown.

All user-controlled Markdown must be rendered through `src/lib/Markdown.svelte`. Never insert parsed or backend-provided HTML directly with `{@html}` elsewhere.

The renderer must retain these safeguards:

- parse with `marked`;
- sanitize with `DOMPurify` before HTML insertion;
- forbid interactive or embedded elements such as scripts, forms, iframes, inputs, buttons, audio, video, and inline styles;
- remove malformed link destinations;
- add `noopener noreferrer` to external links;
- lazy-load images;
- keep the ESLint exception for `{@html}` local to the sanitized renderer only.

When extending Markdown behavior, test hostile input such as script tags, event-handler attributes, and JavaScript URLs in addition to normal headings, lists, links, tables, images, blockquotes, and code blocks.

## Localization

The interface supports Russian and English. Russian source strings are the lookup keys and English translations live in `src/lib/i18n.ts`.

For every new user-visible string:

1. write the Russian source string at the call site;
2. add its English mapping to `src/lib/i18n.ts`;
3. render it through the translation store or helper;
4. use locale-aware date, number, and byte formatting helpers;
5. verify both languages, including long English labels and Russian plural behavior.

Do not hardcode English-only or Russian-only UI, including placeholders, ARIA labels, errors, empty states, and confirmation copy. Protocol enum values, version identifiers, slugs, API versions, and license identifiers remain untranslated.

Locale preference is stored under `gbm.locale`; the legacy storage key exists only for migration. The document language must continue to update when locale changes.

## UI and visual language

Follow Material Design 3 as a system of hierarchy, semantic roles, interaction states, and adaptive composition. Do not reduce it to large rounded cards or pastel colors.

### Product character

GBM uses a restrained technical marketplace style:

- green is the primary product color;
- warm tertiary color is used sparingly for meaningful contrast;
- the four-tile brand mark and restrained voxel/code motifs provide product identity;
- technical values may use the existing monospace stack;
- dense workflows remain calm and readable;
- decorative treatment must never compete with project identity, compatibility, moderation status, or the next action.

Do not introduce generic purple gradients, glassmorphism, glow effects, decorative KPI dashboards, fake testimonials, excessive shadows, or a card around every group.

### Tokens and components

Use the semantic tokens already defined in `src/styles.css`:

- primary, secondary, tertiary, surface, outline, error, and corresponding `on-*` roles;
- surface elevation through tonal containers before shadows;
- the existing shape scale from 4 px through 28 px and full pills;
- the established spacing rhythm based mainly on 4, 8, 12, 16, 24, 32, 48, and 64 px.

Extend existing primitives before creating one-off variants: buttons, fields, tabs, status treatments, state views, dialogs, lists, and navigation. Extend `Icon.svelte` with a matching stroked SVG icon instead of installing an icon library.

Use one filled primary action per local action group. Use native controls and semantics where possible. A destination is navigation; a mutation is an action; a tab is a sibling view of the same entity; a status is data, not a decorative chip.

### Responsive behavior

The application uses:

- a persistent drawer on expanded screens;
- a compact rail at medium widths;
- a top app bar and bottom navigation on compact screens;
- responsive grids and single-column task flows where space requires them.

Breakpoints exist to protect content, not to identify device brands. Test meaningful changes around compact, medium, and expanded widths, plus short viewport heights and 200% zoom. Preserve logical DOM order, selected state, scroll context, and an understandable Back path.

### Interaction states

Every async region or mutation must account for:

- immediate feedback;
- pending state and duplicate-submit prevention;
- a stable success result;
- a specific, recoverable error;
- retained user input after recoverable failure;
- retry, correction, or navigation to the resulting entity.

Skeletons should mirror the final geometry. Empty and no-results states must explain the condition and offer the next relevant action. Search query and compatibility filters should survive URL updates and be shareable.

Use motion only to clarify state or navigation. Prefer `transform` and `opacity`, keep transitions short, and respect `prefers-reduced-motion`. Do not add perpetual motion, parallax, cursor effects, bounce, or full-screen entrance cascades.

## Accessibility requirements

Accessibility is a release requirement, not a follow-up task.

- Prefer native HTML landmarks, headings, links, buttons, form fields, labels, lists, tables, and dialogs.
- Keep the primary flow fully keyboard operable.
- Maintain visible `:focus-visible` treatment.
- Give icon-only buttons an accessible name.
- Use color together with text or iconography for status and errors.
- Keep text contrast at least 4.5:1 and important non-text boundaries at least 3:1.
- Preserve zoom, system theme, forced colors, and reduced-motion preferences.
- Announce asynchronous status where appropriate without unnecessary focus hijacking.
- Keep touch targets large enough and avoid hover-only behavior.
- Implement expected keyboard navigation for tabs and dialogs.

Automated checks do not replace a manual keyboard and responsive-browser pass.

## Legal and content-policy constraints

`EULA.md` is part of the product and must remain reachable from authentication flows.

Do not weaken or silently remove these established terms:

- administration may restrict or remove project visibility under publication rules;
- prohibited or restricted material includes Nazi symbolism, sexual content, promotion of illegal drugs, stolen code, and rights violations;
- every mod defaults to GNU GPL version 3 when no different valid license is declared;
- source code published by the platform is public under the declared license, with GNU GPL version 3 as the default;
- backend developers state that they do not collect telemetry or unrelated analytics, only data required for authentication and service operation;
- legal inquiries go to `legal+gmb@ywfl.dev`.

Do not create a duplicate `LICENSE` file in this frontend. The canonical license is expected in the parent repository at `../LICENSE`; the EULA may reference it and the official GNU license page.

Treat legal copy as user-provided product policy. Make only requested or strictly necessary wording changes, and call out any ambiguity rather than inventing legal commitments.

## Engineering conventions

- Keep TypeScript strict and avoid `any` unless an external boundary makes it unavoidable and it is locally justified.
- Reuse protocol-derived types from `src/types.ts`.
- Put reusable domain logic in testable helpers rather than duplicating it across components.
- Preserve the current Svelte component style unless a migration is explicitly requested; do not mix paradigms casually.
- Keep route pages focused on orchestration and use `src/lib/` for reusable behavior and components.
- Avoid large unrelated refactors during a targeted feature change.
- Preserve existing user changes in a dirty worktree.
- Do not edit backend or parent-project files unless the task explicitly requires it.
- Never hand-edit generated protobuf, gRPC, or gateway output.
- Do not commit generated `dist/` output unless explicitly requested.
- Add dependencies only when they materially improve correctness or maintainability; use `bun add` and commit the updated `bun.lock` when version control is in scope.
- Do not expose tokens, credentials, personal data, archives, or environment secrets in logs, screenshots, tests, or fixtures.

## Definition of done

A change is complete only when:

- it solves the requested user outcome with real behavior;
- it respects backend data, permissions, and lifecycle rules;
- Russian and English UI are both complete;
- loading, empty, error, success, and disabled states remain coherent;
- keyboard and responsive behavior are preserved;
- unsafe Markdown or HTML cannot bypass the shared sanitizer;
- no unrelated files were overwritten;
- `bun run check`, `bun run lint`, `bun test`, and `bun run build` pass;
- meaningful visual changes have been inspected in a browser at expanded and compact widths;
- the final handoff states what changed, what was verified, and any genuine backend limitation.
