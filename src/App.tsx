import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Sidebar } from './components/Sidebar';
import { NoteList } from './components/NoteList';
import { NoteEditor } from './components/NoteEditor';
import { AIPanel } from './components/AIPanel';
import { CommandPalette } from './components/CommandPalette';
import { 
  supabase, 
  syncNoteToSupabase, 
  syncFolderToSupabase, 
  deleteNoteFromSupabase, 
  deleteFolderFromSupabase, 
  fetchInitialDataFromSupabase 
} from './lib/supabase';
import { 
  Note, 
  Folder, 
  ViewMode, 
  Theme, 
  SortBy, 
  SortOrder, 
  SaveStatus, 
  AIMessage, 
  DEFAULT_FOLDERS, 
  STORAGE_KEY,
  AI_MEMORY_KEY,
  GoogleUser
} from './types';
import { Menu, Sparkles, FolderClosed, Plus, FileText, Edit2, LogOut, CheckCircle2, Lock } from 'lucide-react';


export default function App() {
  // Main state
  const [notes, setNotes] = useState<Note[]>([]);
  const [folders, setFolders] = useState<Folder[]>(DEFAULT_FOLDERS);
  const [activeNoteId, setActiveNoteId] = useState<string | null>(null);
  const [activeFolder, setActiveFolder] = useState<string>('all');
  const [activeTag, setActiveTag] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<ViewMode>('editor');
  const [theme, setTheme] = useState<Theme>('light');
  
  // UI state
  const [showRightPanel, setShowRightPanel] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sortBy, setSortBy] = useState<SortBy>('updatedAt');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('saved');
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);

  // Splash screen
  const [showSplash, setShowSplash] = useState(true);
  const [splashProgress, setSplashProgress] = useState(0);
  const [splashStatus, setSplashStatus] = useState("Loading...");

  // User auth
  const [googleUser, setGoogleUser] = useState<GoogleUser | null>(null);
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [loginRequiredReason, setLoginRequiredReason] = useState("");
  const [pendingCallback, setPendingCallback] = useState<(() => void) | null>(null);
  const [customEmail, setCustomEmail] = useState("");
  const [customName, setCustomName] = useState("");

  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // AI chat
  const [aiHistory, setAiHistory] = useState<AIMessage[]>([]);
  const [aiExternalPrompt, setAiExternalPrompt] = useState<string>('');

  // Mobile nav
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [mobileNoteListOpen, setMobileNoteListOpen] = useState(true);

  // Initialize and retrieve data from LocalStorage and Supabase
  useEffect(() => {
    try {
      const persisted = localStorage.getItem(STORAGE_KEY);
      if (persisted) {
        const parsed = JSON.parse(persisted);
        if (parsed.notes) setNotes(parsed.notes);
        if (parsed.folders) setFolders(parsed.folders);
        if (parsed.theme) setTheme(parsed.theme);
        if (parsed.activeFolder) setActiveFolder(parsed.activeFolder);
        if (parsed.sidebarCollapsed !== undefined) setSidebarCollapsed(parsed.sidebarCollapsed);
      } else {
        // Seed initial notes
        const seedNotes: Note[] = [
          {
            id: 'welcome-note-1',
            title: 'Welcome to NoteFlow',
            content: `Hey there! Welcome to NoteFlow.

This is your first note. Here's what you can do:

- Create folders to organize your notes
- Use #tags to categorize things (like #work or #personal)
- Try the AI assistant for help with writing
- Your work auto-saves as you type
- Export notes as text, markdown, HTML, or PDF

Quick shortcuts:
- Ctrl+N: New note
- Ctrl+J: Open AI
- Ctrl+B: Make text bold
- Ctrl+H: Highlight text

Switch to preview mode to see how this looks formatted. Happy writing!`,
            folderId: 'all',
            tags: ['welcome', 'guide'],
            pinned: true,
            archived: false,
            trashed: false,
            createdAt: Date.now() - 3600000,
            updatedAt: Date.now() - 1000000,
            wordCount: 170,
            charCount: 1100
          },
          {
            id: 'welcome-note-2',
            title: 'Meeting Brainstorming Template 💡',
            content: `### Agenda Items
1. Product Architecture rollout
2. Gemini LLM grounding integration points
3. Beta Launch timelines

### Outstanding Thoughts #ideas
- Integrate Google Maps pinning Grounding API in phase 2 releases.
- Establish customized color skins for tags.

### Key Decisions
- Standardized on node-standard bundled commonJS compiler configs over manual JS modules.`,
            folderId: 'ideas',
            tags: ['ideas', 'meeting'],
            pinned: false,
            archived: false,
            trashed: false,
            createdAt: Date.now() - 7200000,
            updatedAt: Date.now() - 3600000,
            wordCount: 65,
            charCount: 420
          }
        ];
        setNotes(seedNotes);
        setActiveNoteId('welcome-note-1');
      }
    } catch (e) {
      console.error("Failed to load local storage index files:", e);
    }

    // Retrieve AI memory
    try {
      const persistedAI = localStorage.getItem(AI_MEMORY_KEY);
      if (persistedAI) {
        setAiHistory(JSON.parse(persistedAI));
      }
    } catch (e) {
      console.error(e);
    }
    
    // Supplement with Supabase Cloud state if configured
    if (supabase) {
      fetchInitialDataFromSupabase().then(data => {
        if (data) {
          if (data.notes && data.notes.length > 0) setNotes(data.notes);
          if (data.folders && data.folders.length > 0) {
            // merge default folders or override, safe to override
            setFolders(data.folders);
          }
        }
      });
    }
  }, []);

  // Splash Screen Progress Simulator
  useEffect(() => {
    if (!showSplash) return;
    const interval = setInterval(() => {
      setSplashProgress((prev) => {
        const next = prev + Math.floor(Math.random() * 20) + 5;
        if (next >= 100) {
          clearInterval(interval);
          setTimeout(() => {
            setShowSplash(false);
          }, 450); // Fluid exit transitions
          return 100;
        }

        if (next < 25) setSplashStatus("Booting NoteFlow workspace...");
        else if (next < 55) setSplashStatus("Applying obsidian distraction-free styling...");
        else if (next < 80) setSplashStatus("Syncing local sandbox backups...");
        else setSplashStatus("Workspace ready.");

        return next;
      });
    }, 180);

    return () => clearInterval(interval);
  }, [showSplash]);

  // Load saved user session
  useEffect(() => {
    try {
      const persistedUser = localStorage.getItem('noteflow-google-user');
      if (persistedUser) {
        setGoogleUser(JSON.parse(persistedUser));
      }
    } catch (e) {
      console.error('Failed to load user:', e);
    }
  }, []);

  const handleGoogleSignIn = (email: string, name: string, pictureUrl?: string) => {
    const user: GoogleUser = {
      email: email.trim(),
      name: name.trim(),
      picture: pictureUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name.trim())}&backgroundColor=6366f1`,
      signedInAt: Date.now()
    };
    setGoogleUser(user);
    localStorage.setItem('noteflow-google-user', JSON.stringify(user));
    setShowLoginModal(false);

    if (pendingCallback) {
      Promise.resolve(pendingCallback()).catch(console.error);
      setPendingCallback(null);
    }
  };

  const handleGoogleSignOut = () => {
    if (confirm("Sign out of your active Google cloud sync profile? Local edits will remain intact.")) {
      setGoogleUser(null);
      localStorage.removeItem('noteflow-google-user');
    }
  };

  const ensureGoogleUser = (callback: () => void, message?: string) => {
    if (googleUser) {
      Promise.resolve(callback()).catch(console.error);
    } else {
      setLoginRequiredReason(message || "Please sign in with Google to authorize Cloud backups and advanced AI Summary features.");
      setPendingCallback(() => callback);
      setShowLoginModal(true);
    }
  };


  // Save changes to LocalStorage periodically
  useEffect(() => {
    if (notes.length === 0 && folders.length === DEFAULT_FOLDERS.length) return;
    try {
      const stateObj = {
        notes,
        folders,
        theme,
        activeFolder,
        sidebarCollapsed
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stateObj));
    } catch (e) {
      console.error("Local storage sync error:", e);
    }
  }, [notes, folders, theme, activeFolder, sidebarCollapsed]);

  // Sync AI History memory
  useEffect(() => {
    if (aiHistory.length === 0) return;
    try {
      localStorage.setItem(AI_MEMORY_KEY, JSON.stringify(aiHistory));
    } catch (e) {
      console.error(e);
    }
  }, [aiHistory]);

  // Sync Class Mode
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'n' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        handleCreateNote();
      }
      if (e.key === 'j' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        setShowRightPanel(prev => !prev);
      }
      if (e.key === 's' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        setSaveStatus('saving');
        setTimeout(() => setSaveStatus('saved'), 600);
      }
      if (e.key === 'k' && (e.ctrlKey || e.metaKey)) {
      if (e.key === 'k' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        setCommandPaletteOpen(prev => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [notes, activeFolder]);

  // Extract dynamically registered hashtag tags inside notes
  const extractTags = (content: string): string[] => {
    const tags: string[] = [];
    const matches = content.matchAll(/(?:^|\s)#([a-zA-Z0-9_-]+)/g);
    for (const match of matches) {
      const t = match[1].toLowerCase();
      // Exclude simple numeric IDs like #1 or general markers
      if (t && isNaN(Number(t)) && !tags.includes(t)) {
        tags.push(t);
      }
    }
    return tags;
  };

  // Re-build overall tag index
  const allTags = Array.from(
    new Set(notes.filter(n => !n.trashed && !n.archived).flatMap(n => n.tags))
  ).sort();

  // Handlers
  // Update note with word/char counting
  const handleUpdateNote = (id: string, updates: Partial<Note>) => {
    setSaveStatus('saving');

    let syncedNote: Note | null = null;

    setNotes(prev => prev.map(note => {
      if (note.id !== id) return note;

      const merged = { ...note, ...updates, updatedAt: Date.now() };

      if (updates.content !== undefined) {
        const text = updates.content;
        merged.charCount = text.length;
        merged.wordCount = text.trim() ? text.trim().split(/\s+/).length : 0;
        
        // Extract tags from content
        const contentTags = extractTags(text);
        const combined = Array.from(new Set([...note.tags, ...contentTags]));
        merged.tags = combined;
      }

      syncedNote = merged;
      return merged;
    }));

    if (syncedNote) {
      syncNoteToSupabase(syncedNote);
    }

    // TODO: maybe use debounce here instead?
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      setSaveStatus('saved');
    }, 500);
  };

  const handleCreateNote = () => {
    const newNote: Note = {
      id: crypto.randomUUID(),
      title: 'Untitled Note',
      content: '',
      folderId: activeFolder === 'all' || activeFolder === 'favorites' || activeFolder === 'archive' || activeFolder === 'trash' ? 'all' : activeFolder,
      tags: [],
      pinned: false,
      archived: false,
      trashed: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      wordCount: 0,
      charCount: 0
    };

    setNotes(prev => [newNote, ...prev]);
    syncNoteToSupabase(newNote);
    setActiveNoteId(newNote.id);
    setViewMode('editor');
    setMobileNoteListOpen(false); // Close list on mobile to focus edit
  };

  // Create Custom Notebook Folder
  const handleCreateFolder = (name: string, icon: string, color: string) => {
    const newFolder: Folder = {
      id: crypto.randomUUID(),
      name,
      icon,
      color
    };
    setFolders(prev => [...prev, newFolder]);
    syncFolderToSupabase(newFolder);
    setActiveFolder(newFolder.id);
  };

  const handleDeleteFolder = (id: string) => {
    if (confirm("Delete this folder? Notes will move to All Notes.")) {
      setFolders(prev => prev.filter(f => f.id !== id));
      deleteFolderFromSupabase(id);
      // Re-associate orphaned notes back to 'all'
      setNotes(prev => prev.map(note => {
        if (note.folderId === id) {
          const detached = { ...note, folderId: 'all' };
          syncNoteToSupabase(detached);
          return detached;
        }
        return note;
      }));
      if (activeFolder === id) {
        setActiveFolder('all');
      }
    }
  };

  // Move Note Notebook
  const handleMoveNote = (id: string, folderId: string) => {
    handleUpdateNote(id, { folderId });
  };

  // Toggle Favorite Star Badge
  const handleTogglePin = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const note = notes.find(n => n.id === id);
    if (note) {
      handleUpdateNote(id, { pinned: !note.pinned });
    }
  };

  // Archive Action
  const handleArchiveNote = (id: string) => {
    const note = notes.find(n => n.id === id);
    if (note) {
      const isCurrentlyArchived = note.archived;
      handleUpdateNote(id, { archived: !isCurrentlyArchived });
      
      // Select another active page if we just archived the current one
      if (activeNoteId === id) {
        setActiveNoteId(null);
      }
    }
  };

  // Trash or Permanently delete notes
  const handleDeleteNote = (id: string) => {
    const note = notes.find(n => n.id === id);
    if (!note) return;

    if (note.trashed) {
      // Permanent delete
      if (confirm("Delete this document completely? This action is permanent and cannot be undone.")) {
        setNotes(prev => prev.filter(n => n.id !== id));
        deleteNoteFromSupabase(id);
        if (activeNoteId === id) setActiveNoteId(null);
      }
    } else {
      // Send to Trash
      handleUpdateNote(id, { trashed: true, pinned: false });
      if (activeNoteId === id) setActiveNoteId(null);
    }
  };

  // Restore note from Trash
  const handleRestoreFromTrash = (id: string) => {
    handleUpdateNote(id, { trashed: false });
    setActiveNoteId(id);
  };

  // Quick Action AI trigger Proxy from note panel
  const handleQuickAction = async (action: string) => {
    const activeNote = notes.find(n => n.id === activeNoteId);
    if (!activeNote || !activeNote.content.trim()) return;

    ensureGoogleUser(async () => {
      setSaveStatus('saving');
      setShowRightPanel(true); // Open AI panel to show response logs

      // Append loading item to AI History chat log for premium visual guidance
      const userPromptMsg: AIMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: `AI Action Trigger request: [${action.toUpperCase()}] on note page "${activeNote.title}"`,
        timestamp: Date.now()
      };
      setAiHistory(prev => [...prev, userPromptMsg]);

      try {
        const response = await fetch('/api/ai/action', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            action,
            text: activeNote.content
          })
        });

        const data = await response.json();
        if (!response.ok) {
          throw new Error(data.error || "Communication error with AI backend.");
        }

        // Append assistant contribution
        const assistantMsg: AIMessage = {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: data.result,
          timestamp: Date.now()
        };
        setAiHistory(prev => [...prev, assistantMsg]);
      } catch (e: any) {
        console.error(e);
        // Append error notification block as assistant response to keep it failsafe
        const errorMsg: AIMessage = {
          id: crypto.randomUUID(),
          role: 'system',
          content: `Service feedback error: ${e.message || "Cloud services timed out. Please configure GEMINI_API_KEY in panel Settings."}`,
          timestamp: Date.now()
        };
        setAiHistory(prev => [...prev, errorMsg]);
      } finally {
        setSaveStatus('saved');
      }
    }, `Google Sign-In is required to run the "${action.toUpperCase().replace(/[^A-Z]/g, ' ')}" AI Summary helper.`);
  };

  // Spawn prompting requests remotely
  const handleAISpawnPrompt = (prompt: string) => {
    setAiExternalPrompt(prompt);
    setShowRightPanel(true);
  };

  // Create new Note page directly from AI outputs
  const handleCreateNewNoteWithContent = (title: string, content: string) => {
    const newNote: Note = {
      id: crypto.randomUUID(),
      title,
      content,
      folderId: 'all',
      tags: ['ai-gen'],
      pinned: false,
      archived: false,
      trashed: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      wordCount: content.trim() ? content.trim().split(/\s+/).length : 0,
      charCount: content.length
    };
    setNotes(prev => [newNote, ...prev]);
    setActiveNoteId(newNote.id);
  };

  // Dynamic content resolution
  const currentActiveNote = notes.find(n => n.id === activeNoteId) || null;

  return (
    <div className={`h-screen w-screen flex overflow-hidden select-none font-sans transition-colors duration-300
      ${theme === 'dark' ? 'bg-slate-950 text-slate-100' : 'bg-slate-50 text-slate-800'}`}
    >
      {/* Splash screen */}
      <AnimatePresence>
        {showSplash && (
          <motion.div
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 1.05 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
            className="fixed inset-0 z-[999] flex flex-col items-center justify-center bg-slate-950 text-slate-100"
          >
            <div className="text-center relative max-w-sm px-6">
              {/* Dynamic decorative ring auroras */}
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 15, repeat: Infinity, ease: 'linear' }}
                className="absolute -inset-6 rounded-full border border-dashed border-indigo-500/20 pointer-events-none"
              />
              <motion.div
                animate={{ rotate: -360 }}
                transition={{ duration: 8, repeat: Infinity, ease: 'linear' }}
                className="absolute -inset-2 rounded-full border border-indigo-500/10 pointer-events-none"
              />

              {/* Glowing logo block */}
              <motion.div
                initial={{ scale: 0.8, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: 0.15, type: 'spring', stiffness: 100 }}
                className="relative z-10 w-24 h-24 bg-gradient-to-tr from-indigo-600 to-violet-600 text-white rounded-3xl flex items-center justify-center mx-auto shadow-2xl shadow-indigo-500/30 border border-indigo-400/20"
              >
                <Sparkles className="w-11 h-11 text-indigo-100 fill-indigo-200 animate-pulse" />
                <div className="absolute top-2 left-2 w-2 h-2 rounded-full bg-indigo-300/40" />
                <div className="absolute top-5 left-2 w-2 h-2 rounded-full bg-indigo-300/40" />
                <div className="absolute top-8 left-2 w-2 h-2 rounded-full bg-indigo-300/40" />
                <div className="absolute top-11 left-2 w-2 h-2 rounded-full bg-indigo-300/40" />
              </motion.div>

              <motion.h1
                initial={{ y: 15, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ delay: 0.35, duration: 0.5 }}
                className="text-2xl mt-6 font-extrabold tracking-widest text-transparent bg-clip-text bg-gradient-to-r from-white via-indigo-100 to-slate-200"
              >
                NOTEFLOW AI
              </motion.h1>

              <motion.p
                initial={{ opacity: 0 }}
                animate={{ opacity: 0.6 }}
                transition={{ delay: 0.5 }}
                className="text-[9px] font-bold text-indigo-300/80 uppercase tracking-widest mt-2"
              >
                Secure Cloud-Persisted Writing workbench
              </motion.p>

              {/* Progress bar container */}
              <div className="mt-8 relative w-48 h-1 bg-slate-900 rounded-full overflow-hidden mx-auto">
                <motion.div
                  className="h-full bg-gradient-to-r from-indigo-500 to-violet-500"
                  style={{ width: `${splashProgress}%` }}
                />
              </div>

              <p className="mt-3.5 text-[10px] font-mono text-slate-400 h-4">
                {splashStatus}
              </p>

              <div className="mt-2 text-[9px] font-mono text-indigo-500/60">
                {splashProgress}% Loaded
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Google Sign-In Accounts Mock/Full Dialog Overlay */}
      {showLoginModal && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/75 backdrop-blur-md p-4">
          <motion.div
            initial={{ scale: 0.95, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className={`w-full max-w-sm rounded-[24px] shadow-2xl p-6 relative overflow-hidden transition-colors border
              ${theme === 'dark' ? 'bg-slate-900 border-slate-800 text-white' : 'bg-white border-slate-100 text-slate-800'}`}
          >
            {/* Authentic Google MultiColor branding decoration */}
            <div className="flex flex-col items-center text-center mt-2 mb-6">
              <div className="flex gap-1.5 justify-center items-center h-11 w-11 bg-slate-50 dark:bg-slate-800 rounded-full shadow-sm border border-slate-200 dark:border-slate-700/50 mb-3 text-lg font-bold">
                <span className="text-blue-500">G</span>
                <span className="text-red-500">o</span>
                <span className="text-yellow-500">o</span>
                <span className="text-blue-500">g</span>
                <span className="text-green-500">l</span>
                <span className="text-red-500">e</span>
              </div>
              <h2 className="text-lg font-bold tracking-tight">Sync with Google Services</h2>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-1.5 max-w-[280px] leading-relaxed">
                {loginRequiredReason || "Secure cloud sync and advanced AI polish features require Google Account verification."}
              </p>
            </div>

            {/* Quick account select (matching user's session metadata) */}
            <div className="space-y-3">
              <p className="text-[10px] uppercase font-bold tracking-wider text-slate-400 dark:text-slate-500 px-1">
                Active Sandbox Profile
              </p>
              
              <button
                onClick={() => handleGoogleSignIn("onejk531@gmail.com", "One JK")}
                className={`w-full flex items-center gap-3.5 p-3 rounded-2xl border text-left transition-all active:scale-98 group cursor-pointer
                  ${theme === 'dark' 
                    ? 'border-slate-800 bg-slate-800/40 hover:bg-slate-800 hover:border-slate-700' 
                    : 'border-slate-200 bg-slate-50/70 hover:bg-slate-100/95 hover:border-slate-300/80'}`}
              >
                <div className="w-10 h-10 rounded-full bg-indigo-600/90 text-white text-sm font-bold flex items-center justify-center shadow-inner group-hover:scale-105 transition-transform">
                  O
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-bold text-xs">One JK</div>
                  <div className="text-[11px] text-slate-400 dark:text-slate-500 truncate font-mono">onejk531@gmail.com</div>
                </div>
                <div className="w-5 h-5 rounded-full border-2 border-indigo-500 flex items-center justify-center text-xs text-indigo-500">
                  <div className="w-2 h-2 rounded-full bg-indigo-500" />
                </div>
              </button>

              <div className="relative flex py-2 items-center">
                <div className="flex-grow border-t border-slate-200 dark:border-slate-800" />
                <span className="flex-shrink mx-3 text-slate-400 dark:text-slate-600 px-1 text-[9px] font-bold uppercase tracking-wider">or verify custom email</span>
                <div className="flex-grow border-t border-slate-200 dark:border-slate-800" />
              </div>

              {/* Custom Signin flow inputs */}
              <div className="space-y-2">
                <input
                  type="text"
                  placeholder="Full Name"
                  value={customName}
                  onChange={(e) => setCustomName(e.target.value)}
                  className={`w-full px-3.5 py-2.5 rounded-xl text-xs border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all
                    ${theme === 'dark' ? 'bg-slate-800/50 border-slate-700 text-white' : 'bg-slate-50 border-slate-200 text-slate-800'}`}
                />
                <input
                  type="email"
                  placeholder="name@gmail.com"
                  value={customEmail}
                  onChange={(e) => setCustomEmail(e.target.value)}
                  className={`w-full px-3.5 py-2.5 rounded-xl text-xs border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all
                    ${theme === 'dark' ? 'bg-slate-800/50 border-slate-700 text-white' : 'bg-slate-50 border-slate-200 text-slate-800'}`}
                />
                
                <button
                  type="button"
                  disabled={!customEmail.trim() || !customName.trim()}
                  onClick={() => handleGoogleSignIn(customEmail, customName)}
                  className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-black dark:bg-indigo-600 dark:hover:bg-indigo-700 text-white text-xs font-bold transition-all shadow-md mt-1 cursor-pointer disabled:opacity-50 disabled:pointer-events-none"
                >
                  Verify Google Profile
                </button>
              </div>
            </div>

            {/* Dismiss options */}
            <div className="mt-5 flex gap-2 justify-between items-center">
              <span className="text-[10px] text-slate-400 flex items-center gap-1">
                <Lock className="w-3 h-3" /> Secure Google Auth
              </span>
              <button
                type="button"
                onClick={() => { setShowLoginModal(false); setPendingCallback(null); }}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-colors cursor-pointer
                  ${theme === 'dark' ? 'text-slate-400 hover:bg-slate-800 hover:text-white' : 'text-slate-500 hover:bg-slate-100/90 hover:text-slate-800'}`}
              >
                Go Offline, Save Locally
              </button>
            </div>
          </motion.div>
        </div>
      )}

      {/* Main layout */}
      
      {/* Sidebar */}
      <div className={`hidden md:block h-full shrink-0 ${sidebarCollapsed ? 'w-16' : 'w-64'}`}>
        <Sidebar
          folders={folders}
          activeFolder={activeFolder}
          setActiveFolder={setActiveFolder}
          activeTag={activeTag}
          setActiveTag={setActiveTag}
          collapsed={sidebarCollapsed}
          setCollapsed={setSidebarCollapsed}
          theme={theme}
          toggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          allTags={allTags}
          onCreateFolder={handleCreateFolder}
          onDeleteFolder={handleDeleteFolder}
          googleUser={googleUser}
          onSignOut={handleGoogleSignOut}
          onSignInTrigger={() => ensureGoogleUser(() => {})}
        />
      </div>

      {/* 2. Note List Column (Middle Left) */}
      {viewMode !== 'focus' && (
        <div className="hidden md:block h-full shrink-0 w-80">
          <NoteList
            notes={notes}
            folders={folders}
            activeNoteId={activeNoteId}
            setActiveNoteId={setActiveNoteId}
            searchQuery={searchQuery}
            setSearchQuery={setSearchQuery}
            activeFolder={activeFolder}
            activeTag={activeTag}
            sortBy={sortBy}
            setSortBy={setSortBy}
            sortOrder={sortOrder}
            setSortOrder={setSortOrder}
            onCreateNote={handleCreateNote}
            onTogglePin={handleTogglePin}
            onMoveNote={handleMoveNote}
            allFolders={folders}
            theme={theme}
          />
        </div>
      )}

      {/* 3. Document Editor/Presentation Workbench (Middle) */}
      <div className="flex-1 h-full flex flex-col min-w-0 bg-white dark:bg-slate-950 relative">
        
        {/* Editor component */}
        <div className="flex-1 h-full min-h-0 relative pb-[60px] md:pb-0">
          <NoteEditor
            note={currentActiveNote}
            onUpdateNote={handleUpdateNote}
            viewMode={viewMode}
            setViewMode={setViewMode}
            saveStatus={saveStatus}
            onDeleteNote={handleDeleteNote}
            onArchiveNote={handleArchiveNote}
            theme={theme}
            onQuickAction={handleQuickAction}
            promptAISpawn={handleAISpawnPrompt}
            googleUser={googleUser}
            onSignInTrigger={(cb) => ensureGoogleUser(cb || (() => {}), "Google Account Sign-In required to configure cloud sync backups.")}
          />
        </div>
      </div>

      {/* 4. Right Copilot Panel (Rightmost) */}
      {showRightPanel && (
        <div className="hidden md:block h-full shrink-0 w-96">
          <AIPanel
            note={currentActiveNote}
            onUpdateNote={handleUpdateNote}
            onCreateNewNoteWithContent={handleCreateNewNoteWithContent}
            history={aiHistory}
            setHistory={setAiHistory}
            theme={theme}
            onClose={() => setShowRightPanel(false)}
            externalPrompt={aiExternalPrompt}
            clearExternalPrompt={() => setAiExternalPrompt('')}
            googleUser={googleUser}
            onSignInTrigger={(cb) => ensureGoogleUser(cb || (() => {}), "Synchronizing Assistant logs requires an connected Google cloud index.")}
          />
        </div>
      )}

      {/* Overlays */}
      <CommandPalette 
        isOpen={commandPaletteOpen}
        onClose={() => setCommandPaletteOpen(false)}
        notes={notes}
        folders={folders}
        theme={theme}
        onSelectNote={(id) => {
          setActiveNoteId(id);
          setViewMode('editor');
          setMobileNoteListOpen(false);
          setMobileSidebarOpen(false);
        }}
        onCreateNote={handleCreateNote}
        onToggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
        onAskAI={() => {
          setShowRightPanel(true);
        }}
      />

      {/* Mobile navigation */}
      
      {/* Mobile sidebar */}
      {mobileSidebarOpen && (
        <div className="fixed inset-0 pb-[60px] z-50 flex md:hidden animate-fade-in">
          <div className="fixed inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setMobileSidebarOpen(false)} />
          <div className="relative w-64 h-full bg-white dark:bg-slate-950 flex flex-col z-10">
            <Sidebar
              folders={folders}
              activeFolder={activeFolder}
              setActiveFolder={(id) => {
                setActiveFolder(id);
                setMobileSidebarOpen(false);
                setMobileNoteListOpen(true);
              }}
              activeTag={activeTag}
              setActiveTag={(tag) => {
                setActiveTag(tag);
                setMobileSidebarOpen(false);
                setMobileNoteListOpen(true);
              }}
              collapsed={false}
              setCollapsed={() => {}}
              theme={theme}
              toggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              allTags={allTags}
              onCreateFolder={handleCreateFolder}
              onDeleteFolder={handleDeleteFolder}
              googleUser={googleUser}
              onSignOut={handleGoogleSignOut}
              onSignInTrigger={() => ensureGoogleUser(() => {})}
            />
          </div>
        </div>
      )}

      {/* 2. Mobile Middle NoteList Slider drawer */}
      {mobileNoteListOpen && !currentActiveNote && (
        <div className="fixed inset-0 pb-[60px] z-40 flex md:hidden animate-fade-in">
          <div className="fixed inset-0 bg-transparent" />
          <div className="relative w-full h-full bg-white dark:bg-slate-950 flex flex-col z-10 pt-4">
            <NoteList
              notes={notes}
              folders={folders}
              activeNoteId={activeNoteId}
              setActiveNoteId={(id) => {
                setActiveNoteId(id);
                setMobileNoteListOpen(false);
              }}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              activeFolder={activeFolder}
              activeTag={activeTag}
              sortBy={sortBy}
              setSortBy={setSortBy}
              sortOrder={sortOrder}
              setSortOrder={setSortOrder}
              onCreateNote={handleCreateNote}
              onTogglePin={handleTogglePin}
              onMoveNote={handleMoveNote}
              allFolders={folders}
              theme={theme}
            />
          </div>
        </div>
      )}

      {/* 3. Mobile Back-to-note button list helper */}
      {mobileNoteListOpen && currentActiveNote && (
        <div className="fixed inset-0 pb-[60px] z-40 flex md:hidden animate-fade-in">
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setMobileNoteListOpen(false)} />
          <div className="relative w-80 h-full bg-white dark:bg-slate-950 flex flex-col z-10 pt-4">
            <NoteList
              notes={notes}
              folders={folders}
              activeNoteId={activeNoteId}
              setActiveNoteId={(id) => {
                setActiveNoteId(id);
                setMobileNoteListOpen(false);
              }}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              activeFolder={activeFolder}
              activeTag={activeTag}
              sortBy={sortBy}
              setSortBy={setSortBy}
              sortOrder={sortOrder}
              setSortOrder={setSortOrder}
              onCreateNote={handleCreateNote}
              onTogglePin={handleTogglePin}
              onMoveNote={handleMoveNote}
              allFolders={folders}
              theme={theme}
            />
          </div>
        </div>
      )}

      {/* 4. Mobile Right AI slider drawer */}
      {showRightPanel && (
        <div className="fixed inset-0 pb-[60px] z-50 flex md:hidden">
          <div className="fixed inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setShowRightPanel(false)} />
          <div className="relative w-80 ml-auto h-full bg-white dark:bg-slate-950 flex flex-col z-10">
            <AIPanel
              note={currentActiveNote}
              onUpdateNote={handleUpdateNote}
              onCreateNewNoteWithContent={handleCreateNewNoteWithContent}
              history={aiHistory}
              setHistory={setAiHistory}
              theme={theme}
              onClose={() => setShowRightPanel(false)}
              externalPrompt={aiExternalPrompt}
              clearExternalPrompt={() => setAiExternalPrompt('')}
              googleUser={googleUser}
              onSignInTrigger={(cb) => ensureGoogleUser(cb || (() => {}))}
            />
          </div>
        </div>
      )}

      {/* 5. Mobile Bottom Navigation Bar */}
      <div className="md:hidden fixed bottom-0 z-[60] w-full h-[60px] pb-safe border-t shadow-[0_-4px_20px_rgba(0,0,0,0.05)] bg-white dark:bg-slate-950 border-slate-200 dark:border-slate-800 flex items-end justify-around px-2 pb-1">
        <button
          onClick={() => {
            setMobileSidebarOpen(true);
            setMobileNoteListOpen(false);
            setShowRightPanel(false);
          }}
          className={`flex flex-col items-center justify-center w-16 h-12 gap-1 transition-colors
            ${mobileSidebarOpen ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'}`}
        >
          <FolderClosed className="w-5 h-5" />
          <span className="text-[9px] font-semibold">Spaces</span>
        </button>

        <button
          onClick={() => {
            setMobileSidebarOpen(false);
            setMobileNoteListOpen(true);
            setShowRightPanel(false);
          }}
          className={`flex flex-col items-center justify-center w-16 h-12 gap-1 transition-colors
            ${mobileNoteListOpen ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'}`}
        >
          <FileText className="w-5 h-5" />
          <span className="text-[9px] font-semibold">Notes</span>
        </button>

        {/* Floating Action Button for Create Note */}
        <div className="relative -top-3">
          <button
            onClick={() => {
              handleCreateNote();
              setMobileNoteListOpen(false);
              setMobileSidebarOpen(false);
              setShowRightPanel(false);
            }}
            className="w-12 h-12 bg-indigo-600 hover:bg-indigo-700 text-white rounded-full flex items-center justify-center shadow-lg shadow-indigo-500/30 transition-transform active:scale-95 border-4 border-white dark:border-slate-950"
          >
            <Plus className="w-6 h-6" />
          </button>
        </div>

        <button
          onClick={() => {
             setMobileSidebarOpen(false);
             setMobileNoteListOpen(false);
             setShowRightPanel(false);
          }}
          className={`flex flex-col items-center justify-center w-16 h-12 gap-1 transition-colors
            ${!mobileSidebarOpen && !mobileNoteListOpen && !showRightPanel ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'}`}
        >
          <Edit2 className="w-5 h-5" />
          <span className="text-[9px] font-semibold">Editor</span>
        </button>

        <button
          onClick={() => {
            setMobileSidebarOpen(false);
            setMobileNoteListOpen(false);
            setShowRightPanel(true);
          }}
          className={`flex flex-col items-center justify-center w-16 h-12 gap-1 transition-colors
            ${showRightPanel ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'}`}
        >
          <Sparkles className={`w-5 h-5 ${showRightPanel ? 'animate-pulse' : ''}`} />
          <span className="text-[9px] font-semibold">Copilot</span>
        </button>
      </div>

    </div>
  );
}
