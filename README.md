# NoteFlow AI

**NoteFlow AI** is a production-ready, offline-first native Android note-taking app
built for developers, professionals, students, and power users. It combines local
persistence, folder/tag organization, real-time cloud sync, and server-side AI
assistance — with solid security throughout and multiple export options.

Stack: **Kotlin · Jetpack Compose + Material 3 · Clean Architecture + MVVM ·
Hilt · Room · DataStore · WorkManager · Navigation Compose · Retrofit + OkHttp ·
Kotlin Serialization · Firebase Auth + Google Sign-In · Supabase PostgreSQL ·
Gemini via secure backend**.

---

## 📱 Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Dependency Injection | Hilt 2.51.1 |
| Database | Room 2.6.1 (offline-first) |
| Preferences | DataStore |
| Background work | WorkManager 2.9.1 |
| Navigation | Navigation Compose |
| Networking | Retrofit 2.11.0 + OkHttp 4.12.0 |
| Serialization | Kotlin Serialization |
| Authentication | Firebase Authentication + Google Sign-In (Credential Manager) |
| Cloud database | Supabase PostgreSQL (Row Level Security) |
| AI | Google Gemini 2.5 Flash via secure backend (`server.ts`) |
| Markdown | Native Compose renderer (no WebView — no XSS) |
| PDF export | Android `PdfDocument` |
| Files | Storage Access Framework (SAF) |
| Voice dictation | Android `SpeechRecognizer` |
| Images | Coil 2.7.0 |
| Build | Gradle Kotlin DSL, minSdk 26 / targetSdk 35 |

---

## 📁 Project Structure

```
NoteFlow AI/
├── android/                 # Native Android app (Gradle Kotlin DSL)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/noteflow/ai/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NoteFlowApplication.kt   # Hilt app, schedules sync
│   │   │   │   ├── navigation/              # Nav graph + Ctrl+K palette
│   │   │   │   ├── di/                      # Hilt modules
│   │   │   │   ├── core/                    # designsystem, common, security
│   │   │   │   ├── data/                    # local, remote, repository
│   │   │   │   ├── domain/                  # model, repository, usecase
│   │   │   │   └── feature/                 # notes, editor, folders, tags,
│   │   │   │   │                              search, ai, export, auth,
│   │   │   │   │                              settings, profile, commandpalette
│   │   │   └── res/                         # themes, strings, icons, manifest
│   │   ├── build.gradle.kts
│   │   ├── AndroidManifest.xml
│   │   ├── google-services.json.example   # Firebase config template
│   │   └── proguard-rules.pro
│   ├── build.gradle.kts
│   ├── gradle.properties                  # noteflow.* config keys
│   ├── gradle/libs.versions.toml
│   ├── settings.gradle.kts
│   ├── .gitignore
│   └── README.android.md                  # build & deploy guide
├── server.ts                              # Secure Gemini backend (Express)
├── src/auth.ts                            # Firebase Admin ID-token verifier
├── supabase-schema.sql                    # Hardened schema + RLS policies
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🔋 Features

### Navigation (all destinations work)
All Notes · Recent Notes · Favorites · Pinned Notes · Archived Notes · Folders ·
Tags · Downloads & Exports · AI Assistant · Settings · User Profile

### Note management
- Create, edit, duplicate, delete (soft-delete to trash with undo)
- Automatic saving while typing; offline-first Room persistence
- Folders with custom emoji icons and colors; multiple tags per note
- Pin / favorite / archive; search across title + content
- Sort by updated/created/title/custom order; word & char counts; reading time
- Empty-state and error-state handling; notes survive restart

### Markdown editor
- Edit / Preview / Focus modes; live Markdown preview
- Formatting toolbar (headings, bold, italic, strikethrough, code, lists,
  checklists, blockquotes, tables, links, horizontal rules)
- Word-count goals, reading-time indicator, auto-save
- Voice dictation via `SpeechRecognizer`; keyboard-aware layouts
- Safe rendering of untrusted content (native Compose, no WebView)

### AI Assistant (powered by Gemini via secure backend — no API key in the app)
- **12 quick actions:** auto-title, summarize, improve, expand, simplify,
  translate, fix, tone, bullets, headline, todos, questions
- **4 generators:** outline, full draft, ideas, continue writing
- Conversational panel with streaming responses, conversation history,
  copy / insert into note, regenerate, cancel, error recovery
- Configurable model selection; per-user rate quotas enforced server-side

### Offline-first synchronization (Supabase + Firebase)
- Room is the local source of truth; all writes happen immediately offline
- Pending ops tracked in a sync outbox; pushed when online
- Last-write-wins merge (remote wins only if strictly newer; local wins ties)
- WorkManager background sync with retry; sync states (Synced/Pending/Syncing/Failed/Local-only)
- Fully usable when Supabase is unavailable

### Export
- TXT, Markdown, HTML (standalone, styled), PDF (`PdfDocument`)
- Share via Android share sheet; save to user-selected directory (SAF)
- Individual or multiple notes; escaped content (no XSS in exports)

### Voice dictation
- Start/stop recording; insert recognized text into the editor
- Microphone permission handling; unavailable-service and error handling

### Command palette (Ctrl+K)
- Create note, search notes, create folder, switch theme, open AI assistant,
  export note, open settings, navigate to favorites, archive note
- Plus editor keyboard shortcuts (Ctrl+Enter save, Ctrl+E preview, Ctrl+F focus)

### Settings (DataStore-persisted)
- Light / dark / system theme; default editor mode; auto-save; word-count goal
- AI model; backend URL; sync; default export format; account management; about

### Security
- No API keys in the app; Firebase ID token on every AI request
- Strict CORS allowlist; rate limiting (20 AI chat/action/min, 10 generate/min)
- Request size caps; input validation; secure error responses
- Markdown XSS defense (http/https links only); HTML export escapes everything
- Prompt-injection defense (system prompts server-side only)
- Supabase Row Level Security: users access only their own `owner`-scoped rows
- No plaintext passwords/keys/tokens stored on disk

---

## 📡 API Endpoints (secure backend)

All AI endpoints require a `Authorization: Bearer <Firebase ID token>` header.

- `GET /api/health` — health check
- `POST /api/ai/chat` — non-streaming chat (20/min per user)
- `POST /api/ai/chat/stream` — SSE streaming chat (`data:` lines, `[DONE]`)
- `POST /api/ai/action` — 12 quick actions (20/min per user)
- `POST /api/ai/generate` — 4 generators (10/min per user)

Request bodies match the Android `AiApi`/`SupabaseApi` Retrofit contracts.

---

## ⚙️ Setup

### 1. Android app configuration

Create `android/app/google-services.json` from `google-services.json.example`:
1. Firebase Console → add Android app (package `com.noteflow.ai`)
2. Download `google-services.json` → `android/app/google-services.json` (gitignored)
3. Enable **Google Sign-In**; copy the **Web client ID**

In `android/gradle.properties` add (never commit real values):

```properties
noteflow.backendUrl=http://10.0.2.2:3000/          # emulator default
noteflow.supabaseUrl=https://your-project.supabase.co
noteflow.supabaseAnonKey=your-supabase-anon-key
noteflow.googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

For release signing, create `android/app/keystore.properties` (gitignored).

### 2. Backend (`server.ts`)

Deploy `server.ts` and set:

```
GEMINI_API_KEY=your-server-side-gemini-key   # never in the Android app
OPENROUTER_API_KEY=optional-fallback-key
ALLOWED_ORIGINS=https://your-app.example.com # production CORS allowlist
PORT=3000
npm install express cors express-rate-limit dotenv @google/genai firebase-admin
npm start
```

### 3. Supabase (cloud sync)

Run `supabase-schema.sql` in the Supabase SQL Editor. It creates `notes` and
`folders` with `owner` (Firebase UID) columns and Row Level Security policies;
anonymous access is denied.

### 4. Build

```bash
cd android
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # signed release APK (needs keystore.properties)
./gradlew bundleRelease          # signed AAB for Play Store
./gradlew testDebugUnitTest      # JVM unit tests
```

Full build/deploy guide: `android/README.android.md`

---

## 🔒 Security

- **No API keys in the app** — Gemini key lives only on `server.ts`; the app sends Firebase ID tokens.
- **CORS allowlist** — backend only accepts configured origins (plus no-Origin for mobile/curl).
- **Rate limits** — per signed-in user: 20 AI chat/action requests/min, 10 generate/min.
- **Input validation** — message/text/prompt size caps; `noteContext` truncated to 10k chars.
- **Markdown XSS defense** — native Compose renderer (no WebView); only `http`/`https` links clickable.
- **Prompt-injection defense** — system instructions server-side only, never exposed to clients.
- **Supabase RLS** — row-level policies on `owner`; `anon` role denied.
- **No plaintext secrets** — tokens/keys never written to disk or logs.

---

## 🧪 Testing

Unit tests (JVM) cover `MarkdownParser`, `TextStats`, `SyncMerge`, and `HtmlExporter`
(`android/app/src/test`). ViewModel, Room, repository, and Compose UI tests run
with Robolectric/JUnit via `./gradlew testDebugUnitTest`.

---

## 🗺️ Roadmap

Done:
- Core note-taking, folders, tags, pin/archive/trash
- Markdown editor (edit/preview/focus), voice dictation
- AI assistant: 12 quick actions + 4 generators + streaming chat
- Offline-first sync with conflict-safe merge, WorkManager
- TXT/MD/HTML/PDF export, SAF, command palette (Ctrl+K)
- Firebase Auth + Google Sign-In; Supabase RLS
- Native Android app (this release)

Future:
- Real-time collaboration
- Enhanced search (full-text ranking)
- Plugin system
- Home-screen widgets

---

## 📜 License

MIT License - Copyright 2026 NoteFlow AI

Free to use, copy, modify, and distribute. No warranty.

---

Made with care. Happy note-taking!
