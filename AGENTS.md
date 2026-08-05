# AGENTS.md

## Область действия и приоритет инструкций

Этот файл действует на весь репозиторий GoMinecraftBridge. Перед изменением файла всегда проверь, нет ли более близкого `AGENTS.md`: для `frontend/**` дополнительно и с большим приоритетом действует `frontend/AGENTS.md`. Прямые требования пользователя имеют приоритет над этими правилами.

Работай только с каноническими исходниками. Не редактируй файлы в `build/`, `.gradle/`, `run/`, `out/`, `dist/`, `node_modules/`, `.tools/` и IDE-кэши. В частности, `versions/1.21.1/build/version-sources/**` создаётся Gradle-задачей `prepareMainSources`; например, канонический исходник `GbmCatalogService` находится в `src/main/java/dev/yawaflua/gominecraftbridge/catalog/GbmCatalogService.java`, а не в `versions/1.21.1/build/version-sources/main/...`.

## Что это за проект

GBM (GoBridgeMinecraft/GoMinecraftBridge) запускает плагины, написанные на Go, внутри Minecraft-процесса. Go-плагин собирается как нативная библиотека через `-buildmode=c-shared`; Java-хост загружает её через JNA и вызывает стабильный C ABI v3. Хотя интеграцию иногда называют JNI, текущая реализация — JNA поверх трёх C-символов `gmb_abi_version`, `gmb_call` и `gmb_free`.

В одном репозитории находятся:

- Fabric-мод для Minecraft `1.21.1` (Java 21) и `26.1.2` (Java 25);
- один shaded Paper-плагин, совместимый с Paper/Purpur `1.21.1`, `1.21.11` и `26.1`;
- публичный Go SDK и пример нативного плагина;
- backend-каталог проектов и версий с HTTP API, авторизацией и модерацией;
- Svelte-фронтенд каталога BridgeMods;
- клиентский менеджер каталога внутри Fabric-мода: поиск, скачивание, проверка версий, установка и обновление Go-модов.

Основной поток выполнения нативного плагина:

```text
Fabric/Paper event
  -> platform runtime/adapter
  -> NativePluginRegistry / LoadedPlugin
  -> NativePluginBackend (JNA)
  -> Go SDK Dispatch
  -> PluginResponse
  -> actions, system calls, logs and subscriptions
```

Поток установки клиентского пакета:

```text
Cloth UI
  -> CatalogTaskController
  -> GbmCatalogService
  -> BackendCatalogClient
  -> SHA-256 verification
  -> safe/atomic PackageInstaller
  -> gbm-package.json + repository.json
  -> client plugin rescan / Mod Menu refresh
```

## Карта репозитория

### Корень, сборка и документация

- `build.gradle` — агрегирующая Gradle-сборка; `build`/`buildAll` собирают обе версии Fabric и Paper, `testAll` запускает все Java-тесты.
- `settings.gradle` — модули `:mc1211`, `:mc2612`, `:paper` и их реальные директории.
- `gradle.properties` — общая версия артефактов и версии Loom/Fabric по умолчанию.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/**` — закреплённый Gradle wrapper; используй его, а не случайный системный Gradle.
- `README.md` — пользовательская установка, API SDK, сборка и ограничения runtime.
- `docs/architecture.md` — границы Java-пакетов и рекомендуемые точки расширения.
- `docs/native-abi.md` — каноническое описание ABI v3, операций, форматов и владения памятью.
- `schema/tick_snapshot.fbs` — схема высокочастотного tick snapshot с идентификатором `GMBS`.
- `.github/workflows/**` — фактические CI/release-команды для Java, SDK, backend и frontend.
- `icon.png`, `LICENSE` — общий ресурс артефактов и корневая лицензия.

### Общая Java/Fabric-часть

`src/main/java/dev/yawaflua/gominecraftbridge/` содержит общую server/common-логику:

- `GoMinecraftBridgeMod.java` — тонкий Fabric entrypoint; только создаёт runtime и регистрирует его.
- `fabric/GbmFabricServerRuntime.java` — привязка lifecycle и игровых событий Fabric к менеджеру Go-плагинов.
- `api/` — публичный Java API: реестр плагинов и расширяемые namespaced system calls. Сохраняй совместимость `GoMinecraftBridgeApi` и package names.
- `backend/PluginBackend.java` — платформенно-независимый интерфейс backend-а выполнения.
- `backend/nativeffi/NativePluginBackend.java` — JNA-вызовы C ABI, лимиты ответа, копирование и освобождение памяти.
- `host/` — discovery и lifecycle: `NativePackageScanner`, `NativePluginRegistry`, `LoadedPlugin`, `GoPluginManager`, snapshots, actions и built-in system calls.
- `protocol/` — transport-only records/enums и кодеки JSON/FlatBuffers. Здесь не должно быть Fabric, Bukkit или UI-логики.
- `management/` — неизменяемые snapshots/results для команд и удалённого admin UI.
- `catalog/` — независимый от Minecraft UI каталог: HTTP transport, настройки, manifests, integrity, безопасная установка и use cases. `GbmCatalogService` — фасад этого слоя.

`src/client/java/dev/yawaflua/gominecraftbridge/client/` содержит только Fabric-client логику:

- `GoMinecraftBridgeClient.java` — клиентский entrypoint;
- `runtime/GbmClientRuntime.java` — composition root клиента;
- `ClientGoPluginManager.java` — lifecycle локальных `client`/`both` Go-плагинов;
- `catalog/CatalogTaskController.java` — фоновые операции каталога и immutable snapshots для UI;
- `plugin/` — хранение конфигурации и обработка ответов/разрешённых client actions;
- `ui/` и `ClothManagementScreen.java` — секции Cloth Config и их композиция;
- `ClientHudState`, `ClientHudLayout` и version-specific `ClientHudRendering` — retained HUD;
- `NativeModMenuEntry`, `GbmModMenuAdapter`, `GoBridgeModMenuIntegration` — отображение нативных Go-плагинов в Mod Menu;
- `mixin/ModsScreenMixin.java` и `src/main/resources/gbm.client.mixins.json` — минимальная интеграция с экраном модов.

`src/main/resources/fabric.mod.json` — общий шаблон метаданных. Версия `1.21.1` имеет overlay в `versions/1.21.1/src/main/resources/`; `26.1.2` использует общий ресурс напрямую.

### Версионные overlays Minecraft

- `versions/1.21.1/build.gradle` копирует общие Java/FlatBuffers-исходники в `build/version-sources/main`, исключая классы с version-specific реализацией. Реальные overlays находятся в `versions/1.21.1/src/**`: `MinecraftVersionAdapter`, admin networking payloads/channels, HUD rendering и ресурсы.
- `versions/26.1.2/build.gradle` компилирует общие исходники напрямую и добавляет overlays из `versions/26.1.2/src/**`. Здесь находятся те же точки совместимости для актуального Minecraft API.
- При изменении Minecraft API не добавляй `if (version)` в общий код, если различие можно локализовать в `compat`, `network` или client rendering overlay.
- Не рассчитывай на один универсальный Fabric JAR: каждая Minecraft ABI получает отдельный артефакт.

### Paper/Purpur

`platforms/paper/` — отдельный Java 21 модуль:

- `PaperBridgePlugin.java` — Bukkit entrypoint и регистрация lifecycle/listeners/commands/channels;
- `PaperGoPluginManager.java` — server lifecycle нативных плагинов на Paper;
- `PaperSnapshotFactory`, `PaperActionExecutor`, `PaperSystemCalls` — адаптеры Bukkit/Paper;
- `PaperAdminCommand`, `PaperAdminMessaging` — `/gbm` и plugin messaging для Fabric-клиента;
- `plugin.yml` — команды, aliases и permission `gbm.admin`;
- `build.gradle` — выбирает только допустимую общую Java-часть, проверяет компиляцию против нескольких Paper API и собирает shaded JAR с relocated Gson/FlatBuffers.

Shared-код, включаемый в Paper, не должен импортировать Minecraft/Fabric classes. Purpur не имеет отдельной реализации: это Paper-compatible runtime.

### Go SDK, ABI и пример

- `sdk/types.go` — ABI version, operation codes и wire DTO. Значения должны совпадать с Java `protocol/Protocol.java` и `docs/native-abi.md`.
- `sdk/plugin.go` — маленькие optional handler interfaces; обязательным остаётся только `Plugin.Metadata()`.
- `sdk/runtime.go` — dispatch операций, decode, panic/error envelope и config updates.
- `sdk/context.go` — накопление actions, system calls, logs и snapshot subscriptions.
- `sdk/native_exports.go` — экспорт C ABI и владение нативной памятью.
- `sdk/output.go` — перехват stdout/stderr/log и flush-barrier.
- `sdk/flatbuffers.go` — преобразование внутреннего FlatBuffers snapshot в публичные SDK types.
- `sdk/internal/fbs/gmb/**` — сгенерированные FlatBuffers Go bindings; вручную не менять.
- `examples/hello-native/main.go` — исполняемая спецификация использования SDK и fixture для Java↔Go integration test.
- `examples/hello-native/build.sh` — сборка `.so`/`.dylib`/`.dll` в `examples/hello-native/dist/`.

`src/main/generated/gmb/**` — сгенерированные и закоммиченные Java FlatBuffers bindings. При изменении `schema/tick_snapshot.fbs` регенерируй Java и Go bindings совместимой версией FlatBuffers (сейчас `25.2.10`), не исправляй generated-код вручную и проверяй interop с обеих сторон.

## Backend: папки, файлы и правила

Backend — отдельный Go module в `backend/` (Go `1.25.8`). Он хранит пользователей, проекты, версии, бинарные архивы, SHA-256, заявки на публикацию, refresh sessions и уведомления.

### Контракт и transport

- `backend/api/project/v1/project.proto` — единственный канонический API-контракт: gRPC service, HTTP annotations, DTO, enums и permissions boundary.
- `backend/gen/project/v1/*.pb.go`, `*.pb.gw.go`, `*_grpc.pb.go` — generated output; никогда не редактировать вручную.
- `backend/docs/swagger/project.swagger.json` — generated OpenAPI; `embed.go`, `internal/httpapi/swagger.go` и `swagger.html` публикуют Swagger UI.
- `backend/internal/httpapi/handler.go` — in-process grpc-gateway handler, middleware, download/`HttpBody` response и HTTP error mapping.
- Backend сейчас поднимает HTTP gateway на `HTTP_HOST:HTTP_PORT`; поля `GRPC_*` существуют в config, но отдельный внешний gRPC listener текущий `cmd/app/main.go` не запускает.

После изменения proto запускай из `backend/` `make generate` и коммить согласованный diff proto + Go bindings + gateway + Swagger. Не меняй frontend API или Java catalog transport в обход proto.

### Приложение и слои

- `backend/cmd/app/main.go` — composition root, signal handling и graceful HTTP shutdown.
- `backend/internal/config/config.go`, `provider.go` — env config и ленивое создание DB/auth/service.
- `backend/internal/api/service.go` — реализация generated server interface и зависимости.
- `auth_endpoints.go` — register/login/refresh/logout/current user.
- `project_endpoints.go` — CRUD/search проектов, versions, upload/download, update checks и submission.
- `admin_endpoints.go` — review, roles, bans, принудительные статусы, delete version и notifications.
- `helpers.go` — валидация, pagination, field masks, access helpers и безопасное отображение DB errors.
- `mapper.go` — единственная точка mapping internal models ↔ protobuf.
- `backend/internal/auth/` — JWT access/refresh, hash/rotation/revocation session и middleware/context identity.
- `backend/internal/models/` — внутренние доменные структуры user/project/version/request/notification.
- `backend/internal/adapters/db.go` — DB interface, который потребляет service/auth.

### Хранилище

- Рабочая реализация — PostgreSQL в `backend/internal/adapters/psql/`.
- `psql.go` управляет pool, transaction context и запускает embedded migrations.
- `query.go` централизует squirrel SQL и scan functions.
- `user.go`, `session.go`, `project.go`, `notification.go` разделены по агрегатам.
- `migrations.go` встраивает `migrations/*.sql` и применяет их через Goose при старте.
- `migrations/00001...00003.sql` — append-only история схемы. Для уже применённой схемы создавай новую migration; не переписывай старую без явного запроса пользователя.
- Архив версии хранится в БД вместе с `content_type`, размером и SHA-256. Не логируй тело архива и не загружай его целиком в дополнительные копии без необходимости.
- `backend/internal/adapters/mongo/mongo.go` — незавершённая legacy-заглушка и не является рабочим backend-ом. Не выбирай `USE_PSQL=false` и не расширяй Mongo-код без отдельной задачи.

### Backend infrastructure

- `.example.env` документирует переменные; реальные `.env`, JWT secrets и credentials не коммитить.
- `compose.yaml` поднимает PostgreSQL, migrator и приложение для локальной проверки.
- `Dockerfile` и `migrator.Dockerfile` — production app и отдельный migration image.
- `Makefile` — pinned protobuf/Goose tools, generation и ручные migration commands.

Сохраняй authorization на service boundary: public, authenticated owner, moderator и admin — разные уровни. Проверяй ownership до мутаций. Не возвращай пользователю внутренние SQL/crypto ошибки. Upload ограничен 64 MiB, avatar — 1 MiB; не ослабляй лимиты, SHA-256 и validation без явной причины.

## Frontend: папки, файлы и правила

Frontend — отдельное Svelte 5 + TypeScript приложение в `frontend/`, собираемое Bun/Vite. Оно использует HTTP bindings из `backend/api/project/v1/project.proto`. Перед любой frontend-правкой полностью прочитай более подробный `frontend/AGENTS.md`; его accessibility, localization, Material 3, Markdown и legal rules обязательны.

Карта файлов:

- `src/main.ts` — mount приложения;
- `src/App.svelte` — route selection и auth guards без внешнего router framework;
- `src/lib/router.ts` — history/location navigation;
- `src/lib/api.ts` — единственный HTTP client, bearer token, single-flight refresh и typed methods;
- `src/lib/session.ts` — hydration/logout/session store;
- `src/types.ts` — frontend-представление protobuf JSON contract;
- `src/lib/i18n.ts` — RU source keys и EN translations; любой новый видимый текст должен работать в обоих языках;
- `src/lib/Markdown.svelte` — единственное место для sanitized `{@html}` через marked + DOMPurify;
- `src/lib/Shell.svelte` — adaptive drawer/rail/top app bar/bottom navigation;
- `src/lib/Dialog.svelte`, `StateView.svelte`, `Status.svelte`, `Icon.svelte` — переиспользуемые primitives;
- `src/pages/Auth.svelte` — регистрация/вход;
- `Discover.svelte` — публичный поиск;
- `MyProjects.svelte`, `CreateProject.svelte`, `ProjectDetail.svelte`, `Release.svelte` — owner/public project flows и публикация версии;
- `Notifications.svelte`, `Moderation.svelte`, `Profile.svelte`, `NotFound.svelte` — уведомления, moderation queue, профиль и fallback route;
- `src/utils.ts` и `utils.test.ts` — formatting/status/slug helpers и их тесты;
- `src/styles.css` — общие semantic tokens, Material 3 primitives, responsive и accessibility states;
- `vite.config.ts` — dev proxy `/v1` к backend; `.env.example` — `VITE_API_BASE_URL` и `VITE_BACKEND_PROXY`;
- `package.json`, `bun.lock`, `tsconfig.json`, `svelte.config.js`, `eslint.config.js` — toolchain; зависимости меняй через Bun и вместе с lockfile;
- `Dockerfile`, `nginx.conf.template` — production static hosting и API routing;
- `EULA.md` — продуктовые/legal правила; `PROMPT.md` и `CONSTITUTION.md` — frontend design guidance.

Frontend не дублирует backend business rules. При изменении API сначала меняется proto/backend, затем `src/types.ts`/`src/lib/api.ts`, затем pages. Токены хранятся только через общий session layer. Не вставляй raw user HTML. Текущий backend не имеет RPC для произвольного ответа владельца в moderation chat; не рисуй неработающий composer без изменения backend-контракта и authorization.

## Ключевые архитектурные инварианты

### Native ABI и lifecycle

1. ABI version и operation codes синхронны между `docs/native-abi.md`, Java `Protocol`, Go `sdk/types.go`, SDK dispatch и всеми platform managers.
2. Tick (`operation 3`) идёт Java → Go через FlatBuffers `GMBS`; control-plane inputs и все responses остаются JSON.
3. Память освобождает та сторона, которая её выделила: Java копирует Go output и обязательно вызывает `gmb_free`; Java object/Go pointer/Go struct границу не пересекают.
4. Вызовы одной библиотеки сериализованы lock-ом. Не убирай synchronization без доказанного безопасного дизайна.
5. Panic даёт `status=panic` и логически отключает plugin; обычная handler error логируется, но не обязана отключать его.
6. Allow-damage/allow-death работают fail-open при отсутствии handler, ошибке, panic или некорректном ответе; успешный `false` запрещает событие.
7. Нативная Go-библиотека намеренно остаётся загруженной до завершения JVM. Reload означает `deinit` + сброс логического state + повторный `init`, но не `NativeLibrary.dispose()` и не замену уже загруженного binary. Физическая выгрузка живого Go runtime небезопасна.
8. Server host принимает `server`/`both`, client host — `client`/`both`; ABI v3 требует явно объявленную среду, которую устанавливает side-specific SDK registration package.
9. Client runtime разрешает только локальные client actions (chat/HUD) и не должен превращаться в обход permissions удалённого сервера.

### Discovery, файлы и каталог

- Fabric server ищет нативные библиотеки в `config/gbm/plugins`, legacy `config/go-minecraft-bridge/plugins` и game `mods`; client — в отдельном `config/gbm/client-plugins`; Paper — `plugins/GBM/plugins`.
- Данные плагинов изолированы по plugin ID. Не смешивай client/server data roots.
- Plugin ID валидируется и уникален; повторная discovery не должна второй раз загружать тот же normalized origin.
- Installer обязан сохранять защиту от Zip Slip/path traversal, выбирать OS extension (`.so`, `.dylib`, `.dll`), писать атомарно и не оставлять partially installed package.
- Перед установкой backend archive всегда сверяется с declared size/SHA-256. Manifest `gbm-package.json` и repository state `repository.json` должны отражать один и тот же успешно записанный binary.
- `GbmCatalogService` остаётся application service без Minecraft UI types. UI и Minecraft screens не выполняют HTTP, file IO или native loading напрямую.

### Потоки и игровые API

- Minecraft world state и действия выполняются на правильном game/server thread. Worker catalog-а публикует snapshots/result, а UI только читает их.
- Entry points остаются тонкими. Feature logic помещай в runtime/service/adapter соответствующего слоя.
- Общий `host`/`protocol` код не зависит от Fabric/Bukkit. Platform-specific преобразования остаются в `fabric`, `paper`, `compat`, `network` или version overlay.
- Для новой native event меняй protocol DTO, Java operation, Go SDK type/interface/dispatch, поддерживаемые runtimes и ABI docs как одну согласованную работу.

## Стиль кода

### Общее правило комментариев

Комментарии пиши только в крайних случаях, когда код или важный неочевидный инвариант невозможно сделать понятным именами и структурой. Не добавляй комментарии, которые пересказывают строку, метод, DTO, очевидный control flow или историю изменений. Сначала улучши имя/декомпозицию; комментарий оставляй только для причины, ограничения ABI, thread-safety, memory ownership, compatibility workaround или другого действительно неочевидного поведения. Не покрывай новые классы и методы шаблонными Javadoc/GoDoc ради количества документации.

### Java и Gradle

- Следуй существующему стилю: tabs для отступов, braces на той же строке, явные типы, небольшие final classes/records и immutable copies на границах.
- Используй records для transport/snapshot values, если не требуется identity или сложный mutable lifecycle.
- Не протаскивай platform types через общие интерфейсы.
- Сохраняй понятные exception messages с operation/path/plugin ID, но не секретами и не содержимым больших payload.
- Не делай широких format/refactor diff рядом с точечной правкой.

### Go

- Всегда применяй `gofmt`; ошибки оборачивай с контекстом через `%w`, где caller должен сохранить cause.
- Передавай `context.Context` в DB/network operations и сохраняй transaction context.
- Не вводи global mutable state, кроме строго синхронизированного ABI registration/output plumbing.
- Существующие wire/database names (`Id`, `Licence` и т. п.) не переименовывай массово: сначала оцени миграцию и совместимость protobuf/JSON/SQL.

### TypeScript/Svelte

- Используй strict TypeScript, существующие stores/helpers и текущий стиль Svelte; не добавляй framework/router/UI library без необходимости.
- Pages отвечают за orchestration, переиспользуемое поведение и UI — за `src/lib/`.
- Новый пользовательский текст: русская строка в call site, английское соответствие в `i18n.ts`.
- Соблюдай существующие semantic CSS tokens и состояния keyboard/loading/error/empty/success.

## Тесты и проверка

Не создавай тесты для каждой мелкой правки, очевидного DTO/getter, простой верстки или механического mapping. Новые тесты нужны только для важной, рискованной логики и регрессий, где ошибка повредит данные, безопасность, ABI или lifecycle. Главный приоритет — загрузка, инициализация, deinit, логический reload/остановка Go-модов, освобождение ABI buffers, обработка panic/error и согласованность Java↔Go. Также оправданы тесты для integrity/atomic installation/Zip Slip, auth/permissions, archive validation и критичного protocol encoding. Физическую выгрузку Go library тестировать или реализовывать не нужно: она намеренно запрещена.

Запускай существующие проверки пропорционально затронутой области; documentation-only изменение не требует сборки.

### Java/Fabric/Paper

```bash
./gradlew :mc1211:test
./gradlew :mc2612:test
./gradlew :paper:test
./gradlew testAll
./gradlew build
```

Полный build требует JDK 25, при этом `mc1211` и Paper выпускают Java 21 bytecode. Для реального ABI integration fixture:

```bash
./examples/hello-native/build.sh
GBM_TEST_LIBRARY="$PWD/examples/hello-native/dist/libhello_native.so" ./gradlew testAll
```

На Windows/macOS подставь созданный `.dll`/`.dylib`. Не заявляй, что native interop проверен, если `GBM_TEST_LIBRARY` не был задан и conditional test был пропущен.

### Go SDK и backend

```bash
find sdk examples/hello-native -type f -name '*.go' -print0 | xargs -0 gofmt -l
(cd sdk && go vet ./... && go test -race ./...)
find backend -type f -name '*.go' -print0 | xargs -0 gofmt -l
(cd backend && go vet ./... && go test -race ./... && go build ./cmd/app)
```

Пустой вывод `gofmt -l` означает, что форматирование корректно. Форматируй через `gofmt -w` только затронутые файлы и не трогай несвязанные пользовательские Go-изменения.

### Frontend

```bash
(cd frontend && bun run check)
(cd frontend && bun run lint)
(cd frontend && bun test)
(cd frontend && bun run build)
```

Для визуальных изменений дополнительно проверь реальный UI в compact и expanded viewport, клавиатуру, 200% zoom, RU/EN, loading/error/empty states. Не утверждай, что browser pass выполнен, если он не выполнялся.

## Рабочий процесс будущего агента

1. Прочитай применимые `AGENTS.md`, `README.md` и профильный документ (`docs/native-abi.md`, proto или frontend guidance).
2. Проверь `git status`; существующие изменения пользователя сохраняй и не перезаписывай.
3. Найди канонический исходник. Если путь содержит `build/`, найди источник в `src/`, `versions/*/src`, schema или proto.
4. До правки проследи полный контракт по слоям. Особенно это обязательно для ABI, protobuf API, version metadata и package paths.
5. Сделай минимальный cohesive diff в правильном слое; не смешивай feature с несвязанным рефакторингом.
6. Не редактируй generated-файлы вручную; меняй schema/proto и регенерируй весь согласованный набор.
7. Добавляй комментарий только при выполнении строгого правила выше. Добавляй новый тест только для действительно важной логики; обязательно запускай релевантные существующие тесты.
8. Если изменился публичный ABI, SDK API, directory layout, команда, HTTP contract или поддерживаемая версия — обнови соответствующие docs/resources в той же работе.
9. Перед сдачей просмотри diff, убедись в отсутствии secrets, binaries, build output и случайного форматирования. В отчёте точно укажи выполненные и пропущенные проверки.

## Запрещённые и опасные изменения

- Не выгружай живую Go shared library и не обещай hot binary replacement без рестарта JVM.
- Не меняй ABI code/JSON field/FlatBuffers field order только на одной стороне.
- Не вызывай Minecraft/Bukkit API из фонового HTTP/catalog thread.
- Не устанавливай архив до проверки размера и SHA-256 и не ослабляй safe extraction.
- Не обходи owner/moderator/admin authorization в HTTP gateway или frontend guards.
- Не логируй JWT, passwords, refresh tokens, archives, config secrets или персональные данные.
- Не коммить `.env`, native binaries, `dist/`, generated Gradle source copies или IDE state.
- Не правь generated protobuf/Swagger/FlatBuffers output вручную.
- Не создавай фиктивный UI или API behavior, которого нет в backend/runtime.
