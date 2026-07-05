# NoteFlow AI Docs

Welcome to **NoteFlow AI** - a full-stack note-taking app built for developers. It combines local storage, folder organization, tag filtering, real-time sync, and server-side AI assistance with solid security features and multiple export options.

---

## 🎨 Visual Preview & Design Philosophy
We designed NoteFlow to be clean and focused, with an Obsidian-inspired layout:
*   **Themes**: Light and dark modes with great contrast
*   **Animations**: Smooth transitions using Framer Motion for a polished feel
*   **Mobile-first**: Works great on phones with collapsible sidebars

---

## 🏗️ Technology Stack

*   **Frontend**: React 19, TypeScript, Tailwind CSS v4, Framer Motion, Lucide icons
*   **Backend**: Express server with CORS and rate limiting, Vite middleware for dev, esbuild for production
*   **Storage**: Browser localStorage, Google OAuth for user sync, Supabase for cloud database
*   **AI**: Google's genai library running server-side (keeps API keys safe)
*   **Exports**: PDF (via jsPDF), plus TXT, MD, and HTML formats
*   **Voice**: Web Speech API for dictation

---

## 🔒 Security

* CORS middleware to control which domains can access the API
* Rate limiting - 20 requests/minute for AI endpoints, 10/minute for generation (prevents abuse)
* Input validation with character limits (50k for chat, 100k for actions)
* DOMPurify for XSS protection in markdown rendering
* Input sanitization to prevent prompt injection attacks
* Secure error messages that don't leak system details

---

## 📁 Project Structure

```
├── server.ts         # Express server with Vite middleware
├── package.json      # Dependencies and scripts
├── metadata.json     # Platform config
├── tsconfig.json     # TypeScript settings
├── vite.config.ts    # Vite setup
├── src/
│   ├── main.tsx      # App entry point
│   ├── App.tsx       # Main app with state and auth
│   ├── types.ts      # TypeScript types and constants
│   ├── index.css     # Tailwind styles and Google fonts
│   ├── lib/
│   │   └── supabase.ts # Supabase client
│   └── components/
│       ├── Sidebar.tsx       # Folders, themes, user profile
│       ├── NoteList.tsx      # Note list with search and sorting
│       ├── NoteEditor.tsx    # Markdown editor with export
│       ├── AIPanel.tsx       # AI chat sidebar
│       └── CommandPalette.tsx # Keyboard shortcuts UI
```

---

## 🔋 Features

### Sync & Storage
- Notes auto-save to localStorage as you type
- Supabase syncs to PostgreSQL when configured
- Google OAuth handles user authentication and cloud profiles

### AI Assistant
Runs server-side using Google's Gemini 2.5 Flash (API keys stay safe on server).

**Quick Actions (12 types):**
- Auto-Title: Generate note titles
- Summarize: Extract key points
- Improve: Polish writing
- Expand/Simplify: Make text longer or shorter
- Translate: Convert to other languages
- Fix: Grammar and spelling
- Tone: Change writing style
- Bullets: Format as markdown lists
- Headline: Create titles
- Todos: Extract action items
- Questions: Generate discussion points

**Generators (4 types):**
- Outline: Create structured outlines
- Draft: Write full drafts
- Ideas: Brainstorm new ideas
- Continue: Keep writing from existing content

### Editor
- Three view modes: Edit, Preview, or Focus
- Full markdown support: headings, bold, italic, code blocks, quotes, lists, etc.
- Live preview with XSS protection
- Voice dictation via Web Speech API
- Formatting toolbar for quick markdown
- Word count goals with progress bars
- Live stats: words, characters, reading time

### Export
- TXT: Simple text format
- MD: Full markdown preserved
- HTML: Complete web page with styling
- PDF: Professional documents with proper formatting

### Productivity
- Command palette (Ctrl+K) for quick actions
- Keyboard shortcuts for common tasks
- Folders with icons and colors
- Tag system for organization
- Pin important notes
- Archive old notes
- Live search across all content

---

## 📡 API Endpoints

All endpoints use JSON.

### GET /api/health
Check if server is running.
```json
{ "status": "healthy", "timestamp": "2026-05-26T23:00:00.000Z" }
```

### POST /api/ai/chat
Chat with AI assistant. Rate limited to 20/min.
```json
{
  "messages": [
    { "role": "user", "content": "Your question here" }
  ],
  "systemContext": "Optional note context"
}
```
Max 50 messages, 50k chars each.

### POST /api/ai/action
Text processing actions. Rate limited to 20/min.

**Actions:** autotitle, summarize, improve, expand, simplify, translate, fix, tone, bullets, headline, todos, questions

```json
{
  "action": "summarize",
  "text": "Text to process",
  "instructions": "Optional parameters"
}
```
Max 100k chars for text, 500 for instructions.

### POST /api/ai/generate
Content generation. Rate limited to 10/min.

**Types:** outline, draft, ideas, continue

```json
{
  "type": "outline",
  "prompt": "Your request",
  "noteContext": "Optional existing content"
}
```

---

## ⚙️ Setup

### 1. Environment Variables
Create `.env` file:

**For Google OAuth:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create project, enable Google Sign-In API
3. Create OAuth 2.0 credentials
4. Add redirect URIs (e.g., `http://localhost:3000/auth/callback`)

```env
# Google AI
GEMINI_API_KEY="your_api_key"
OPENROUTER_API_KEY="optional_fallback_key"

# Google OAuth
GOOGLE_CLIENT_ID="your_client_id"
GOOGLE_CLIENT_SECRET="your_client_secret"
GOOGLE_OAUTH_REDIRECT_URI="http://localhost:3000/auth/callback"

# Database (optional)
VITE_SUPABASE_URL="your_url"
VITE_SUPABASE_ANON_KEY="your_key"

# Security (optional)
ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173"
```

### 2. Run Development
```bash
npm install
npm run dev
# Frontend: http://localhost:5173
# API: http://localhost:3000
```

### 3. Production Build
```bash
npm run build
npm start
# App: http://localhost:3000
```

### 4. Other Commands
```bash
npm run clean  # Remove build files
npm run lint   # TypeScript check
```

---

## ⌨️ Keyboard Shortcuts

- Ctrl+N: New note
- Ctrl+J: Open AI
- Ctrl+S: Save
- Ctrl+F: Search
- Ctrl+B: Bold
- Ctrl+I: Italic
- Ctrl+H: Highlight

---

## 🛡️ Security Tips

- Keep API keys secret (never in client code)
- Rate limiting prevents abuse
- Set ALLOWED_ORIGINS in production
- DOMPurify sanitizes user content
- Input validation blocks long requests
- Local storage is less secure than server
- Use Google OAuth for better security
- Backup important notes regularly

---

## 📦 Dependencies

**Main:**
- @google/genai (^2.4.0) - Google AI
- @supabase/supabase-js (^2.106.2) - Database
- express (^4.21.2) - Server
- react (^19.0.1) - UI
- vite (^6.2.3) - Build tool

**Security:**
- cors (^2.8.6) - CORS middleware
- dompurify (^3.4.6) - XSS protection
- express-rate-limit (^8.5.2) - Rate limiting
- dotenv (^17.2.3) - Environment vars

**Utils:**
- jspdf (^4.2.1) - PDF export
- motion (^12.23.24) - Animations
- lucide-react (^0.546.0) - Icons
- tailwindcss (^4.1.14) - Styling

---

## 🎯 Code Quality

Overall: **A- (really solid)**

The codebase is well-structured with TypeScript, good security, and clean architecture. Main things to improve: add some tests and clean up console.logs.

**Lines of code:** ~3,585+ TypeScript/React
**Technical debt:** Low (no TODOs found)
**Security:** Good protection in place
**Tests:** None yet - manual testing only

**Tech stack:**
- TypeScript 5.8+ (strict)
- React 19 + hooks
- Tailwind CSS v4
- Context + local state
- Vite + esbuild

**Could be better:**
- Add unit tests
- Remove console.logs
- More accessibility stuff
- Performance monitoring

---

## 🚀 Roadmap

Done:
- Core note-taking
- Markdown with live preview
- AI writing help
- Export to TXT, MD, HTML, PDF
- Security (CORS, rate limiting, XSS protection)
- Voice dictation
- Word count tracking
- Multiple view modes

Future:
- Real-time collaboration
- Better search
- Plugin system
- Mobile apps

---

## 📜 License

MIT License - Copyright 2026 NoteFlow AI

Free to use, copy, modify, and distribute. No warranty.

---

Made with care. Happy note-taking!
