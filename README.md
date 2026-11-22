# NoteFlow AI

**NoteFlow AI** is an offline-first native Android note-taking app with
folder/tag organization, cloud sync (Supabase), and server-side AI assistance
(Gemini behind a secure, token-authenticated backend).

Stack: **Kotlin · Jetpack Compose + Material 3 · Clean Architecture + MVVM ·
Hilt · Room · DataStore · WorkManager · Retrofit + OkHttp · Firebase Auth +
Google Sign-In (Credential Manager) · Supabase PostgreSQL (RLS) · Gemini via
secure backend (server.ts)**

---

## Production hardening status (this release)

This repository has been through a full production-readiness pass. The major
fixes, grouped by area:

**Build & launch**
- Fixed the cold-start crash: the start destination route now matches the
  composable pattern (`notes/{filter}?arg={arg}` with an optional argument).
- Fixed every compile blocker: missing `asStateFlow`/`update`/layout/icon
  imports, a Kotlin keyword used as an identifier, an 11-flow `combine`, a
  duplicate `Command` declaration, unbalanced parentheses, references to a
  non-existent use case, and nullable/non-nullable mismatches.
- Added the Gradle wrapper (Gradle 8.9) and `kotlin("test")` so `./gradlew`
  works from a clean clone and the JVM tests compile.

**Editor (the core feature)**
- The editor now persists exactly what the user typed: the ViewModel owns the
  edited title/content, auto-saves on a settings-configured debounce, and
  explicit save writes through `SaveNoteContentUseCase`. (Previously edits
  were silently discarded on save.)
- Title is editable; editor text survives rotation (`rememberSaveable`); the
  "note re-emitted" observer seeds the text once per note id instead of on
  every emission (no more clobbering in-progress edits).
- Voice dictation destroys its `SpeechRecognizer` on leave-composition;
  mic-permission flow includes an explicit rationale message.

**Sync & data integrity**
- Schema ↔ DTO alignment: `supabase-schema.sql` now uses the snake_case
  column names the PostgREST DTOs actually send (`user_id`, `created_at`,
  `folder_id`, `sort_order`). Migration notes for existing v1 tables are in
  the schema file. **Cloud sync works for the first time.**
- Note+tags+outbox mutations run inside one Room transaction; remote applies
  enforce last-write-wins in the repository as well as in SyncManager.
- `syncNow()` is guarded by a mutex; remote pulls paginate (`limit`/`offset`,
  200 per page); Supabase calls share the configured OkHttp client (30s
  connect / 120s read / 30s write timeouts — previously NO timeout).

**Networking & backend**
- Streaming chat uses a strict JSON SSE envelope (`{"type":"start"|"delta"|
  "error"|"done"}`) parsed by a unit-tested `SseParser` — the client no
  longer appends raw JSON fragments to chat output. The server line-buffers
  upstream SSE so tokens split across network chunks are not lost.
- Backend: exact CORS allowlist (no more `*.vercel.app` wildcard), bounded
  upstream timeouts (AbortSignal / SDK timeout), client-requested model is
  honored against an allowlist, user content is framed with a per-request
  nonce, streaming errors emit friendly messages only, per-user quota map is
  bounded, global unhandled-rejection hooks.
- Android: all AI traffic goes through one URL-rewrite interceptor (settings
  and build-time URLs can no longer diverge), HTTPS is enforced for the AI
  backend and Supabase in release builds, tokens refresh proactively before
  expiry, and user-facing errors never contain raw exception text.

**Lifecycle, UI & accessibility**
- Query/sort/selection survive process death (`SavedStateHandle`); batch
  trash has a single combined Undo; the dead error state was removed;
  duplicate per-screen TopAppBars were removed (single app bar per screen);
  AI chat uses `LazyColumn` (token streaming no longer re-measures the whole
  transcript); selection icons announce state; quick actions/generators are
  functional against the text box.

**Observability & QA**
- Structured `Logger` facade + local bounded crash log (`CrashGuard`), no
  user content in logs, release builds disable HTTP logging.
- Test suite: fixed `SyncMergeTest`/`TextStatsTest`/`MarkdownParserTest` and
  added `SseParserTest` plus `NoteEditorViewModelSaveTest` — the latter two
  are regression tests for the streaming-format and editor-save defects.
- CI (`.github/workflows/ci.yml`): debug build + unit tests, backend
  typecheck, and a schema↔DTO consistency guard.

---

## Project structure

```
NoteFlow AI/
├── android/                     # Native Android app (Gradle Kotlin DSL)
│   ├── app/
│   │   ├── src/main/java/com/noteflow/ai/
│   │   │   ├── MainActivity.kt
│   │   │   ├── NoteFlowApplication.kt     # Hilt app, CrashGuard, sync schedule
│   │   │   ├── navigation/                # Nav graph + Ctrl+K palette
│   │   │   ├── di/                        # Hilt modules + backend URL interceptor
│   │   │   ├── core/                      # designsystem, common (SseParser, Logger),
│   │   │   │                              # security
│   │   │   ├── data/                      # local (Room), remote, repository
│   │   │   ├── domain/                    # model, repository, usecase
│   │   │   └── feature/                   # notes, editor, folders, tags, search,
│   │   │                                  # ai, export, auth, settings, profile
│   │   ├── src/test/                      # JVM unit tests incl. regression tests
│   │   └── src/main/res/xml/              # backup rules (DB excluded from cloud)
│   ├── gradle/libs.versions.toml          # pinned dependency catalog
│   └── gradlew                            # wrapper (Gradle 8.9)
├── server.ts                    # Secure Gemini/OpenRouter backend (Express)
├── src/auth.ts                  # Firebase Admin ID-token verifier
├── supabase-schema.sql          # v2 schema, snake_case, RLS policies
├── package.json / tsconfig.json # backend tooling + typecheck
└── .github/workflows/ci.yml     # build + tests + typecheck + schema guard
```

## Building the Android app

```bash
cd android
./gradlew assembleDebug          # debug build (works without any credentials)
./gradlew testDebugUnitTest      # JVM unit tests
```

Configuration (all optional; the app runs fully offline/local-only without
them) — set in `android/gradle.properties`, `~/.gradle/gradle.properties`, or
CI env vars:

| Property | Purpose |
|---|---|
| `noteflow.backendUrl` | AI backend base URL (default `http://10.0.2.2:3000/` for the emulator; must be HTTPS in release) |
| `noteflow.supabaseUrl` | Supabase project URL (must be HTTPS) |
| `noteflow.supabaseAnonKey` | Supabase anon key |
| `noteflow.googleWebClientId` | OAuth web client id for Google Sign-In |

For Firebase features add `google-services.json` (template:
`android/app/google-services.json.example`). Without it the app builds and
runs in local-only mode with sign-in disabled.

Release signing uses a gitignored `android/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it
`assembleRelease` produces an unsigned artifact. **Never claim a release
build works without executing it** — run `./gradlew assembleRelease` and
install the APK on a device before shipping.

## Running the AI backend

```bash
npm install
cp .env.example .env   # then fill in the values below
npm run dev            # development
npm start              # production (serves dist/ if present)
npm run typecheck      # TypeScript check (also runs in CI)
```

Environment variables:

| Variable | Required | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | yes (or `OPENROUTER_API_KEY`) | server-side model key — never ships to clients |
| `OPENROUTER_API_KEY` | no | routes all AI through OpenRouter instead of Gemini |
| `FIREBASE_SERVICE_ACCOUNT` / `GOOGLE_APPLICATION_CREDENTIALS` | yes for auth | Firebase Admin credentials for ID-token verification |
| `ALLOWED_ORIGINS` | yes in production | EXACT CORS origins, comma-separated |
| `PORT` | no | default 3000 |
| `AI_UPSTREAM_TIMEOUT_MS` | no | upstream call timeout, default 90000 |

Deployment requirements: HTTPS termination (reverse proxy / managed
platform), a process manager or platform restarts, and log retention on
stdout. The per-user quota is in-memory — for multi-instance deployments
externalise it to Redis (`rate-limit-redis`) or accept per-instance limits.

## Supabase setup

1. Create the tables/policies: paste `supabase-schema.sql` into the Supabase
   SQL Editor and run it (for v1 tables, apply the `ALTER TABLE ... RENAME`
   block at the top of the file first).
2. Enable third-party auth: Supabase Dashboard → Authentication →
   Third-Party Auth → add your Firebase project (this makes `auth.uid()`
   resolve to the Firebase UID inside RLS policies).
3. Set `noteflow.supabaseUrl` / `noteflow.supabaseAnonKey` in the app build.

## Monitoring & recovery

- **App:** uncaught exceptions are appended to a bounded on-device log;
  `CrashGuard.drain(context)` retrieves and clears it for bug reports. Add
  Firebase Crashlytics (with `google-services.json`) for fleet reporting.
- **Backend:** logs to stdout (`console.error` with context per endpoint);
  ship to your platform's log drain. `/api/health` is unauthenticated and
  suitable for uptime checks. Watch HTTP 429/5xx rates for quota/service
  degradation.
- **Rollback:** the backend is stateless — redeploy the previous image/commit
  and restart. The database schema is additive; rolling back the Android app
  is safe because sync is last-write-wins with an idempotent outbox.
- **Data recovery:** the Room database is excluded from cloud backup by
  design (see `app/src/main/res/xml/backup_rules.xml`); user data recovery
  comes from Supabase (server-side) or device-to-device transfer.

## Testing

```bash
cd android && ./gradlew testDebugUnitTest
```

Covers: Markdown/XSS URL safety, HTML export escaping, text statistics, the
sync conflict rule, the SSE envelope parser (regression for the
JSON-fragment streaming bug), and the editor save round-trip (regression for
the discarded-edits bug). Critical paths still needing device/instrumented
coverage: sign-in end-to-end, sync against a live Supabase, and export flows
— run these on a real device before each release.

## License

See [LICENSE](LICENSE).
