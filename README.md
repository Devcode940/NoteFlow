# NoteFlow AI Docs

Welcome to **NoteFlow AI**, a modern, secure, and visually polished full-stack note-taking workbench. This application combines local-first draft resilience, modular folders, responsive tag query systems, and real-time cloud-sync profiles alongside a server-side Gemini AI writing co-pilot.

---

## 🎨 Visual Preview & Design Philosophy
NoteFlow AI is built to avoid visual clutter and "AI-slop"—instead pursuing **architectural honesty** and the **Obsidian-style distraction-free layout**:
*   **Theming & Contrast**: Two pixel-perfect mode profiles: a clean, high-contrast Slate light theme, and deep Obsidian Dark Mode (`bg-slate-950`).
*   **Interactive Motion Engine**: Crafted using `motion` animations:
    *   *Fluid Entrance Splash*: A 2.2-second entry sequence featuring responsive progress loaders and decorative orbital indicators.
    *   *Staggered Animations*: Note lists gracefully animate into place on folders or tags switching, ensuring smooth, non-flickering visual rhythm.
*   **Touch & Compact Layouts**: Built mobile-responsive first, featuring bento drawer sidebar configurations that slide elegantly into place.

---

## 🏗️ Technology Stack

*   **Frontend**: React 19, TypeScript, Tailwind CSS, Framer Motion (`motion/react`), Lucide React icons.
*   **Backend Server**: Node.js Express server running live Vite integration middleware in dev mode and esbuild bundling at production compilation.
*   **Persistent Storage**:
    *   *Offline First*: Automated synchronization with browser `localStorage`.
    *   *Google Workspace Sandbox*: Simulated multi-user sync with dynamic initialized avatars and verification models.
    *   *Supabase Realtime*: Full PostgreSQL cloud database storage, enabling zero-config integration when credentials are functional in `.env`.
*   **Core Intelligence**: Fully server-side integrated Google `@google/genai` model engine, supporting both native **Gemini 3.5 Flash** and OpenRouter alternatives.

---

## 📁 Source Architecture

```text
├── server.ts              # Custom full-stack Express server + Viteware middleware
├── package.json           # Application dependencies and build scripts
├── metadata.json          # Platform access rules, client capabilities & descriptor
├── tsconfig.json          # Strict TypeScript configuration compiling parameters
├── src/
│   ├── main.tsx           # Primary virtual root initializer
│   ├── App.tsx            # Global controller, layout shell, auth controllers & core state
│   ├── types.ts           # Type-safe model contracts & default application settings
│   ├── index.css          # Tailwind directives, google custom fonts & global classes
│   ├── lib/
│   │   └── supabase.ts    # Supabase cloud engine connection & database upserts
│   └── components/
│       ├── Sidebar.tsx    # Left sidebar containing user sync profiles, folders & theme toggles
│       ├── NoteList.tsx   # Middle column with live search, filters, order sorting & animations
│       ├── NoteEditor.tsx # Core distraction-free writing slate with markdown exports & live target targets
│       ├── AIPanel.tsx    # Right sidebar containing chat history, system instructions, and memory
│       └── CommandPalette.tsx # Global action console for modal commands and prompt short-troughs
```

---

## 🔋 Feature Walkthrough

### 1. Dual-Sync Architecture
1. **Local-First Draft Mode**: Changes are automatically written to `localStorage` as you type, creating zero-latency local resilience.
2. **Supabase Core Integration**: Upserts and deletions are streamed live to PostgreSQL tables when `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` are defined.
3. **Google Sign-In Accounts Profile**: Authenticates active sync status, unlocking premium background formatting actions on the server-side.

### 2. High-Performance AI Co-Pilot
The co-pilot system utilizes **Gemini 3.5 Flash** server-side, safeguarding API keys since no keys are exposed to the browser.
*   **Conversational Chat Sidebar**: Accepts context prompts from the active note page, feeding metadata structurally into chat completion histories.
*   **Note-Taking Quick Actions**:
    *   *Auto-Title*: Automatically generates a short, relevant header for empty notes of a minimum threshold.
    *   *Summarize*: Distills detailed ideas into structured markdown lists with takeaways.
    *   *Improve*: Polishes styling, syntax pacing, and vocabulary choices without changing information.
    *   *Expand / Simplify*: Elongates documents or strips down jargon.
    *   *Auto-Translate*: Seamlessly translates note segments into target languages.
    *   *Task-Checklist Extractor*: Processes standard prose and extracts actionable checklists (`- [ ] Task`).

### 3. Productivity Command Console
*   Triggers via clicking the Search/Navigation controls or using `ctrl+k` / `ctrl+shift+p`.
*   Provides rapid access to note creators, folder switching, and custom AI actions without taking your hands off the keyboard.

---

## 📡 API Specification Sheet

All endpoints process and expect application payload JSON streams:

### `GET /api/health`
Verifies runtime systems status.
*   **Response payload**:
    ```json
    { "status": "healthy", "timestamp": "2026-05-26T23:00:00.000Z" }
    ```

### `POST /api/ai/chat`
Communicates with the conversational memory assistant.
*   **Request body**:
    ```json
    {
      "messages": [
        { "role": "user", "content": "How do I optimize React dependency hook arrays?" }
      ],
      "systemContext": "Note Title: 'React Hooks'\nNote Content:\n'Always clean up event listeners.'"
    }
    ```
*   **Response payload**:
    ```json
    { "content": "To optimize hook arrays, always stabilize references..." }
    ```

### `POST /api/ai/action`
Executes procedural text utilities.
*   **Request body**:
    ```json
    {
      "action": "summarize",
      "text": "The quick brown fox jumps over the lazy dog repeatedly because...",
      "instructions": ""
    }
    ```
*   **Response payload**:
    ```json
    { "result": "* Key Takeaway: Agile activity by the fox..." }
    ```

---

## ⚙️ Setup and Operational Guide

### 1. Configuration Setup
Create a `.env` in the root (matching `.env.example` configurations):
```env
GEMINI_API_KEY="YOUR_GOOGLE_GEMINI_KEY"
VITE_SUPABASE_URL="https://your_uuid.supabase.co"
VITE_SUPABASE_ANON_KEY="your_anon_public_key"
```

### 2. Standard Scripts
*   **Development Server**: Boots Vite's server-side listener in real time.
    ```bash
    npm run dev
    ```
*   **Production Compilation**: Automatically coordinates type safety tests, compiles the frontend bundle, and builds the backend into a compact single CJS script (`dist/server.cjs`) using esbuild.
    ```bash
    npm run build
    ```
*   **Executable Start**: Launches the production application container instantly.
    ```bash
    npm start
    ```

---

*NoteFlow AI is built to combine local writing workflows with state-of-the-art server features robustly. Enjoy writing distraction-free!*
