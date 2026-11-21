# NoteFlow AI — Android build & deployment

This directory contains the native Android implementation of NoteFlow AI
(Kotlin, Jetpack Compose, Clean Architecture + MVVM, Hilt, Room, DataStore,
WorkManager, Retrofit, Kotlin Serialization).

## 1. Prerequisites (host machine)

The sandbox cannot build Android (no JDK/SDK, and Maven/Google/Gradle hosts are
blocked). Build on a workstation with:

- Android Studio (or command-line tools) — **JDK 17**, **Android SDK 35**
- `ANDROID_HOME` / `sdk.dir` pointing at the SDK
- The Android SDK platforms: `android-35`, build-tools `35.0.0`
- An emulator (API 26+) or a physical device (minSdk 26)

Install via Android Studio → SDK Manager, or:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "cmdline-tools;latest" "emulator"
```

## 2. External configuration (required)

Do **not** commit real secrets. Create these files from the templates:

### `android/app/google-services.json` (Firebase)
1. Create a Firebase project at https://console.firebase.google.com
2. Add an Android app (package name `com.noteflow.ai`)
3. Download `google-services.json` → `android/app/google-services.json`
4. Enable **Google Sign-In** in Firebase → Authentication → Sign-in method
5. Note your **Web client ID** (OAuth 2.0 client IDs → Web client)

### `android/gradle.properties`
Append (create if missing):

```properties
# Backend URL used by the AI repository (emulator default)
noteflow.backendUrl=http://10.0.2.2:3000/

# Supabase (PostgREST) — optional for cloud sync
noteflow.supabaseUrl=https://your-project.supabase.co
noteflow.supabaseAnonKey=your-supabase-anon-key

# Google Sign-In web client ID (from Firebase console)
noteflow.googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

### `android/app/keystore.properties` (release signing only)

```properties
storeFile=/path/to/noteflow-release-keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Generate a release keystore:

```bash
keytool -genkeypair -v -keystore noteflow-release-keystore.jks \
  -alias noteflow-release -keyalg RSA -keysize 2048 -validity 10000
```

### Backend (server.ts)
Deploy `server.ts` (Node.js + Express) and set:

```
GEMINI_API_KEY=your-server-side-gemini-key   # never in the Android app
OPENROUTER_API_KEY=optional-openrouter-key   # fallback model provider
ALLOWED_ORIGINS=https://your-app.example.com # production CORS allowlist
PORT=3000
npm install express cors express-rate-limit dotenv @google/genai firebase-admin
npm start
```

Android calls the backend over HTTPS (or `http://10.0.2.2:3000` on emulator).
All AI requests carry the Firebase ID token; the backend verifies it before
calling Gemini. **No Gemini key is ever embedded in the APK.**

### Supabase (cloud sync)
Run `supabase-schema.sql` in the Supabase SQL Editor. It creates `notes` and
`folders` with `owner` (Firebase UID) columns and Row Level Security policies
so users can only access their own rows. Anonymous access is denied.

## 3. Build

```bash
cd android

# Debug APK (installs on emulator/device)
./gradlew assembleDebug

# Signed release APK (requires keystore.properties)
./gradlew assembleRelease

# Android App Bundle (for Play Store)
./gradlew bundleRelease

# Unit tests (JVM, no device needed)
./gradlew testDebugUnitTest
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` (signed if keystore present)
- `app/build/outputs/bundle/release/app-release.aab`

## 4. Architecture summary

```
app/
  MainActivity.kt            # Compose entry point, edge-to-edge, theme from DataStore
  NoteFlowApplication.kt     # Hilt application, schedules WorkManager sync
  navigation/                # Navigation Compose graph + Ctrl+K command palette
  di/                        # Hilt modules (AppModule, DatabaseModule)
  feature/
    notes/                   # Note list + CRUD + undo-delete + search/sort
    editor/                  # Markdown editor (edit/preview/focus), voice dictation
    folders/                 # Folders with emoji icon + color
    tags/                    # Tag list
    search/                  # Full-text search
    ai/                      # AI assistant: 12 quick actions + 4 generators + streaming chat
    export/                  # TXT/MD/HTML/PDF export via Storage Access Framework
    auth/                    # Firebase Auth + Google Sign-In (Credential Manager)
    settings/                # DataStore-backed settings
    profile/                 # User profile + sign-out
  core/
    designsystem/            # Theme, typography, folder color/icon palettes
    common/                  # Markdown parser/renderer, HTML/Text/PDF exporters, TextStats
    security/                # Input validation, size limits, XSS/prompt-injection defenses
  data/
    local/                   # Room entities, DAOs, database, DataStore prefs
    remote/                  # Retrofit APIs, AI repository (backend-only), auth, sync
    repository/              # Repository implementations
  domain/
    model/                   # Note, Folder, AiQuickAction (12), AiGeneratorType (4), etc.
    repository/              # Repository interfaces
    usecase/                 # Note use cases
```

## 5. Security model

- **No API keys in the app**: Gemini key lives only on the backend; the app sends
  Firebase ID tokens to `server.ts`, which verifies them before calling Gemini.
- **CORS allowlist**: backend only accepts configured origins (plus no-Origin for
  mobile/curl).
- **Rate limits**: 20 AI chat/action requests/min, 10 generate/min, per signed-in user.
- **Input validation**: message/text/prompt size caps; `noteContext` truncated to 10k chars.
- **Markdown XSS defense**: native Compose renderer (no WebView); only `http`/`https`
  links are clickable; all exported HTML escapes note content.
- **Prompt injection**: system instructions are server-side only; never exposed to clients.
- **Supabase RLS**: row-level policies on `owner` (Firebase UID); `anon` role denied.
- **No plaintext secrets**: tokens/keys never written to disk or logs.

## 6. Offline-first sync

- Room is the local source of truth; all writes happen locally and immediately.
- Mutations enqueue idempotent ops in a `sync_queue` table.
- `SyncManager` pushes the outbox, then pulls remote changes applying
  last-write-wins (remote wins only if strictly newer; local wins ties).
- WorkManager schedules periodic background sync (15-min minimum) and on-demand sync.
- The app remains fully usable when Supabase is offline; sync states
  (Synced/Pending/Syncing/Failed/Local-only) are shown per note.

## 7. Testing

Unit tests cover `MarkdownParser`, `TextStats`, `SyncMerge`, and `HtmlExporter`
(`app/src/test`). ViewModel, Room, repository, and Compose UI tests are located
in `app/src/test` and `app/src/androidTest` and run with Robolectric/JUnit.

## 8. Release checklist

- [ ] `google-services.json` present (Firebase configured)
- [ ] `noteflow.googleWebClientId` set in `gradle.properties`
- [ ] `keystore.properties` present for release signing
- [ ] Backend deployed with `GEMINI_API_KEY` and CORS allowlist
- [ ] Supabase schema + RLS applied
- [ ] `./gradlew bundleRelease` succeeds and produces a signed AAB
- [ ] Tested: sign-in, note CRUD offline, AI quick actions, streaming chat,
      TXT/MD/HTML/PDF export, voice dictation, command palette (Ctrl+K)
