import React, { useRef, useEffect } from 'react';
import { 
  Bold, 
  Italic, 
  Heading1, 
  Heading2, 
  Code, 
  List, 
  Star,
  Archive,
  Trash2,
  Download,
  Eye,
  Edit2,
  Maximize2,
  Minimize2,
  Tag,
  Plus,
  RefreshCcw,
  Check,
  ChevronRight,
  Sparkles,
  Columns,
  CheckSquare,
  Mic,
  Clock,
  Target,
  Cloud,
  CloudOff,
  ShieldCheck,
  AlertTriangle
} from 'lucide-react';
import { Note, ViewMode, SaveStatus, GoogleUser } from '../types';

interface NoteEditorProps {
  note: Note | null;
  onUpdateNote: (id: string, updates: Partial<Note>) => void;
  viewMode: ViewMode;
  setViewMode: (mode: ViewMode) => void;
  saveStatus: SaveStatus;
  onDeleteNote: (id: string) => void;
  onArchiveNote: (id: string) => void;
  theme: 'light' | 'dark';
  onQuickAction: (action: string) => void;
  promptAISpawn: (prompt: string) => void;
  googleUser?: GoogleUser | null;
  onSignInTrigger?: (callback?: () => void) => void;
}

export const NoteEditor: React.FC<NoteEditorProps> = ({
  note,
  onUpdateNote,
  viewMode,
  setViewMode,
  saveStatus,
  onDeleteNote,
  onArchiveNote,
  theme,
  onQuickAction,
  promptAISpawn,
  googleUser,
  onSignInTrigger
}) => {

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [tagInput, setTagInput] = React.useState('');
  const [showExportMenu, setShowExportMenu] = React.useState(false);

  // Goal Tracking
  const [isEditingGoal, setIsEditingGoal] = React.useState(false);
  const [goalInput, setGoalInput] = React.useState('');

  // Speech Recognition
  const [isListening, setIsListening] = React.useState(false);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    if (typeof window !== 'undefined' && ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)) {
      const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      recognitionRef.current = new SpeechRecognition();
      recognitionRef.current.continuous = true;
      recognitionRef.current.interimResults = false;

      recognitionRef.current.onresult = (event: any) => {
        let finalTranscript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          if (event.results[i].isFinal) {
            finalTranscript += event.results[i][0].transcript + ' ';
          }
        }
        if (finalTranscript) {
           insertFormatting(finalTranscript, '');
        }
      };

      recognitionRef.current.onerror = () => setIsListening(false);
      recognitionRef.current.onend = () => setIsListening(false);
    }
  }, []);

  const toggleListening = () => {
    if (!recognitionRef.current) {
      alert('Speech recognition not supported in this browser.');
      return;
    }
    if (isListening) {
      recognitionRef.current.stop();
    } else {
      recognitionRef.current.start();
      setIsListening(true);
    }
  };

  const handleSaveGoal = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!note) return;
    const count = parseInt(goalInput, 10);
    if (!isNaN(count) && count > 0) {
      onUpdateNote(note.id, { targetWordCount: count });
    } else {
      onUpdateNote(note.id, { targetWordCount: undefined });
    }
    setIsEditingGoal(false);
  };

  // Auto focus content if note changes
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [note?.id]);

  if (!note) {
    return (
      <div className={`flex-1 h-full flex flex-col items-center justify-center select-none text-center p-8
        ${theme === 'dark' ? 'bg-slate-950 text-slate-400' : 'bg-white text-slate-500'}`}
    >
      <div className="max-w-md space-y-4 animate-fade-in">
        <div className="w-16 h-16 rounded-2xl bg-indigo-50 dark:bg-indigo-950/20 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mx-auto shadow-sm">
          <BookOpenIcon />
        </div>
        <h2 className="text-lg font-bold text-slate-800 dark:text-white mt-4">Welcome to NoteFlow Workspace!</h2>
        <p className="text-xs text-slate-400 dark:text-slate-500 leading-relaxed max-w-sm">
          Select an existing folder from the left directory to open a file, or create a new note page to start indexing and organizing your ideas instantly.
        </p>
        <div className="flex justify-center flex-wrap gap-2 text-[10px] font-medium text-slate-400 bg-slate-50 dark:bg-slate-900/40 p-3 rounded-lg border border-dashed dark:border-slate-800">
          <span className="px-1.5 py-0.5 rounded bg-white dark:bg-slate-800 shadow-sm border dark:border-slate-700">Ctrl + N (New File)</span>
          <span className="px-1.5 py-0.5 rounded bg-white dark:bg-slate-800 shadow-sm border dark:border-slate-700">Ctrl + J (Ask AI Writer)</span>
        </div>
      </div>
    </div>
  );
}

  // Formatting insertions logic (Markdown inserts)
  const insertFormatting = (symbolBefore: string, symbolAfter = symbolBefore) => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = textarea.value;

    const selectedText = text.substring(start, end);
    const replacement = symbolBefore + selectedText + symbolAfter;

    onUpdateNote(note.id, {
      content: text.substring(0, start) + replacement + text.substring(end)
    });

    // Reset selection focus point
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(
        start + symbolBefore.length,
        end + symbolBefore.length
      );
    }, 10);
  };

  // Tag creation
  const handleTagSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanTag = tagInput.trim().toLowerCase().replace(/[^a-zA-Z0-9]/g, '');
    if (cleanTag && !note.tags.includes(cleanTag)) {
      const updatedTags = [...note.tags, cleanTag];
      onUpdateNote(note.id, { tags: updatedTags });
      setTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    const updatedTags = note.tags.filter(t => t !== tagToRemove);
    onUpdateNote(note.id, { tags: updatedTags });
  };

  // Simple Markdown to HTML preview generator
  const getRenderedHTML = (content: string) => {
    if (!content.trim()) {
      return `<p class="italic text-slate-400">This page is currently empty. Start typing to see formatting here...</p>`;
    }

    // Escape raw tag structures to prevent XSS
    let html = content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Headings
    html = html.replace(/^# (.*?)$/gm, '<h1>$1</h1>');
    html = html.replace(/^## (.*?)$/gm, '<h2>$1</h2>');
    html = html.replace(/^### (.*?)$/gm, '<h3>$1</h3>');

    // Horizontal Ruler
    html = html.replace(/^---$/gm, '<hr />');

    // Bold, Italic, Highlight, Blocks
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    html = html.replace(/==(.*?)==/g, '<mark class="bg-amber-100 text-slate-900 px-1 rounded">$1</mark>');
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Blockquotes
    html = html.replace(/^&gt;\s(.*?)$/gm, '<blockquote><p>$1</p></blockquote>');

    // Simple bullets & items lists
    html = html.replace(/^\-\s\[\s\]\s(.*?)$/gm, '<li class="list-none flex items-center gap-2"><input type="checkbox" disabled class="w-3.5 h-3.5 mt-0.5 rounded border-slate-300" /> $1</li>');
    html = html.replace(/^\-\s\[x\]\s(.*?)$/gm, '<li class="list-none flex items-center gap-2 text-slate-400 line-through"><input type="checkbox" checked disabled class="w-3.5 h-3.5 mt-0.5 rounded border-slate-300" /> $1</li>');
    html = html.replace(/^\-\s(?!\[)(.*?)$/gm, '<li>$1</li>');
    html = html.replace(/(<li.*?>.*?<\/li>)/gs, '<ul>$1<\/ul>');
    // Remove consecutive sub-lists borders
    html = html.replace(/<\/ul>\s*<ul>/g, '');

    // Convert newlines to breaks where appropriate
    html = html.split('\n\n').map(p => {
      if (p.startsWith('<h1>') || p.startsWith('<h2>') || p.startsWith('<h3>') || p.startsWith('<ul>') || p.startsWith('<blockquote>') || p.startsWith('<hr />')) {
        return p;
      }
      return `<p>${p.replace(/\n/g, '<br />')}</p>`;
    }).join('');

    return html;
  };

  // Exporters download
  const handleExport = (format: 'txt' | 'md' | 'html') => {
    let outputText = '';
    let mimeType = 'text/plain';
    let fileExtension = 'txt';

    if (format === 'txt') {
      outputText = `${note.title}\n\n${note.content}`;
      mimeType = 'text/plain';
      fileExtension = 'txt';
    } else if (format === 'md') {
      outputText = `# ${note.title}\n\n${note.content}`;
      mimeType = 'text/markdown';
      fileExtension = 'md';
    } else if (format === 'html') {
      const bodyHtml = getRenderedHTML(note.content);
      outputText = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>${note.title}</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 650px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #1e293b; }
    h1 { font-size: 2em; border-bottom: 1px solid #e2e8f0; padding-bottom: 10px; }
    blockquote { border-left: 4px solid #cbd5e1; padding-left: 15px; font-style: italic; color: #475569; }
    code { background: #f1f5f9; padding: 2px 4px; border-radius: 4px; font-family: monospace; }
    mark { background-color: #fef08a; padding: 1px 3px; border-radius: 3px; }
  </style>
</head>
<body>
  <h1>${note.title}</h1>
  ${bodyHtml}
</body>
</html>`;
      mimeType = 'text/html';
      fileExtension = 'html';
    }

    const blob = new Blob([outputText], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${note.title.toLowerCase().replace(/[^a-z0-9]/g, '_') || 'note'}.${fileExtension}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setShowExportMenu(false);
  };

  return (
    <div className={`flex-1 h-full flex flex-col min-w-0 transition-colors duration-300 relative
      ${theme === 'dark' ? 'bg-slate-950 text-slate-100' : 'bg-white text-slate-800'}`}
    >
      {/* Primary Toolbar Actions Banner */}
      <header className={`h-16 px-4 md:px-6 border-b flex items-center justify-between shrink-0 select-none
        ${theme === 'dark' ? 'border-slate-900' : 'border-slate-100'}`}>
        
        {/* Save Status & View controls */}
        <div className="flex items-center gap-3">
          {googleUser ? (
            <div className={`flex items-center justify-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-bold transition-all duration-500 ${
              saveStatus === 'saving' 
                ? 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/20 text-amber-600 dark:text-amber-400 shadow-[0_0_8px_rgba(245,158,11,0.15)] animate-pulse' 
                : 'bg-emerald-50 dark:bg-emerald-950/20 border-emerald-200 dark:border-emerald-800 text-emerald-600 dark:text-emerald-500 animate-fade-in'
            }`}>
              {saveStatus === 'saving' ? (
                <span className="flex items-center gap-1">
                  <RefreshCcw className="w-3 h-3 animate-spin" /> Syncing Cloud...
                </span>
              ) : (
                <span className="flex items-center gap-1">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" /> Synced to Google
                </span>
              )}
            </div>
          ) : (
            <button
              onClick={onSignInTrigger}
              className={`flex items-center justify-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-bold transition-all duration-300 cursor-pointer hover:border-indigo-400 dark:hover:border-indigo-700 ${
                saveStatus === 'saving' 
                  ? 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/20 text-amber-600 dark:text-amber-400' 
                  : 'bg-slate-50 dark:bg-indigo-950/10 dark:hover:bg-indigo-950/20 hover:bg-indigo-50/20 border-slate-200 dark:border-slate-800 text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 shadow-sm'
              }`}
              title="Backup your notebook entries safely with your Google Account."
            >
              {saveStatus === 'saving' ? (
                <span className="flex items-center gap-1">
                  <RefreshCcw className="w-3 h-3 animate-spin" /> Saving locally...
                </span>
              ) : (
                <span className="flex items-center gap-1">
                  <CloudOff className="w-3.5 h-3.5 text-rose-400 shrink-0" /> Draft: Local Only
                </span>
              )}
            </button>
          )}

          {/* View Modes Selection bar */}
          <div className="flex bg-slate-100 dark:bg-slate-900 border dark:border-slate-800 p-0.5 rounded-lg shrink-0">
            <button
              onClick={() => setViewMode('editor')}
              className={`flex items-center gap-1 px-3 py-1 rounded-md text-xs font-semibold transition-all
                ${viewMode === 'editor' 
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-sm' 
                  : 'text-slate-500 dark:text-slate-400 hover:text-indigo-500'}`}
            >
              <Edit2 className="w-3 h-3" />
              <span className="hidden sm:inline">Editor</span>
            </button>
            <button
              onClick={() => setViewMode('preview')}
              className={`flex items-center gap-1 px-3 py-1 rounded-md text-xs font-semibold transition-all
                ${viewMode === 'preview' 
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-sm' 
                  : 'text-slate-500 dark:text-slate-400 hover:text-indigo-500'}`}
            >
              <Eye className="w-3 h-3" />
              <span className="hidden sm:inline">Presentation</span>
            </button>
            <button
              onClick={() => setViewMode('focus')}
              className={`flex items-center gap-1 px-3 py-1 rounded-md text-xs font-semibold transition-all
                ${viewMode === 'focus' 
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-sm' 
                  : 'text-slate-500 dark:text-slate-400 hover:text-indigo-500'}`}
            >
              <Maximize2 className="w-3 h-3" />
              <span className="hidden sm:inline">Focus</span>
            </button>
          </div>
        </div>

        {/* Note Pin/Archive/Trash Actions options */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => onUpdateNote(note.id, { pinned: !note.pinned })}
            className={`p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-900 transition-colors
              ${note.pinned ? 'text-amber-500' : 'text-slate-400 dark:text-slate-500 hover:text-amber-500'}`}
            title={note.pinned ? 'Unpin this Note' : 'Pin Note to Top'}
          >
            <Star className={`w-4 h-4 ${note.pinned ? 'fill-current' : ''}`} />
          </button>

          <button
            onClick={() => onArchiveNote(note.id)}
            className={`p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-900 transition-colors
              ${note.archived ? 'text-teal-500' : 'text-slate-400 dark:text-slate-500 hover:text-teal-500'}`}
            title={note.archived ? 'Send Back to Notebooks' : 'Archive this Page'}
          >
            <Archive className="w-4 h-4" />
          </button>

          <button
            onClick={() => onDeleteNote(note.id)}
            className={`p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-900 transition-colors
              ${note.trashed ? 'text-rose-500' : 'text-slate-400 dark:text-slate-500 hover:text-rose-500'}`}
            title={note.trashed ? 'Restore Item permanently' : 'Move Note to Trash'}
          >
            <Trash2 className="w-4 h-4" />
          </button>

          {/* Export button menu toggler */}
          <div className="relative">
            <button
              onClick={() => setShowExportMenu(!showExportMenu)}
              className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-900 text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
              title="Download File"
            >
              <Download className="w-4 h-4" />
            </button>

            {showExportMenu && (
              <div card-id="export" className={`absolute right-0 mt-2 w-40 rounded-xl border p-2 shadow-lg z-30 select-none animate-fade-in
                ${theme === 'dark' ? 'bg-slate-900 border-slate-800 text-slate-100' : 'bg-white border-slate-200'}`}>
                <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400 px-2 py-1 select-none">Export Note</p>
                <button 
                  onClick={() => handleExport('txt')}
                  className="w-full text-left px-2 py-1.5 text-xs font-semibold rounded hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Plain Text (.txt)
                </button>
                <button 
                  onClick={() => handleExport('md')}
                  className="w-full text-left px-2 py-1.5 text-xs font-semibold rounded hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Markdown (.md)
                </button>
                <button 
                  onClick={() => handleExport('html')}
                  className="w-full text-left px-2 py-1.5 text-xs font-semibold rounded hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Web Document (.html)
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main Text Work Panel */}
      <div className="flex-1 overflow-y-auto px-4 py-6 md:px-8 custom-scrollbar">
        <div className="max-w-3xl mx-auto flex flex-col h-full gap-4">
          
          {/* Note Title Input */}
          <div className="flex items-center group relative w-full">
            <input
              type="text"
              placeholder={note.trashed ? "Trashed Note Item" : "Document Title..."}
              disabled={note.trashed}
              value={note.title}
              onChange={(e) => onUpdateNote(note.id, { title: e.target.value })}
              className="w-full bg-transparent font-extrabold text-2xl md:text-3xl tracking-tight focus:outline-none placeholder-slate-300 dark:placeholder-slate-700 select-text pr-12"
            />
            {(!note.trashed && note.content) && (
              <button
                onClick={() => {
                  const triggerAutoTitle = async () => {
                    try {
                      const res = await fetch('/api/ai/action', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'autotitle', text: note.content.slice(0, 1000) })
                      });
                      if (res.ok) {
                        const data = await res.json();
                        if (data.result) {
                          onUpdateNote(note.id, { title: data.result.trim() });
                        }
                      }
                    } catch (e) { console.error('Failed to auto-title', e); }
                  };

                  if (googleUser) {
                    triggerAutoTitle();
                  } else if (onSignInTrigger) {
                    onSignInTrigger(triggerAutoTitle);
                  } else {
                    triggerAutoTitle();
                  }
                }}
                className="absolute right-0 opacity-0 group-hover:opacity-100 p-2 text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/40 rounded-lg transition-all cursor-pointer"
                title="Auto-generate Title (AI)"
              >
                <Sparkles className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* Formative Markup Options bar (editor mode only) */}
          {viewMode !== 'preview' && !note.trashed && (
            <div className={`flex items-center flex-wrap gap-1 p-1 rounded-xl items-center border select-none shrink-0
              ${theme === 'dark' ? 'bg-slate-950/25 border-slate-900' : 'bg-slate-50/50 border-slate-100'}`}>
              
              <button
                onClick={() => insertFormatting('**')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Bold (Ctrl+B)"
              >
                <Bold className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => insertFormatting('*')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Italic (Ctrl+I)"
              >
                <Italic className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => insertFormatting('==', '==')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 font-bold"
                title="Highlight Word (Ctrl+H)"
              >
                <span className="text-xs bg-yellow-200/50 text-slate-800 px-1 rounded">Hl</span>
              </button>
              <div className="w-px h-4 bg-slate-200 dark:bg-slate-800 mx-1" />
              <button
                onClick={() => insertFormatting('# ')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Heading 1"
              >
                <Heading1 className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => insertFormatting('## ')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Heading 2"
              >
                <Heading2 className="w-3.5 h-3.5" />
              </button>
              <div className="w-px h-4 bg-slate-200 dark:bg-slate-800 mx-1" />
              <button
                onClick={() => insertFormatting('- ')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Bullet point"
              >
                <List className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => insertFormatting('- [ ] ')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Checklist Item"
              >
                <CheckSquare className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => insertFormatting('`')}
                className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500"
                title="Code block"
              >
                <Code className="w-3.5 h-3.5" />
              </button>

              <div className="w-px h-4 bg-slate-200 dark:bg-slate-800 mx-1" />

              <button
                onClick={toggleListening}
                className={`p-1.5 rounded-lg font-bold flex items-center gap-1 transition-all ${isListening ? 'bg-red-100 text-red-600 dark:bg-red-900/30' : 'hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500'}`}
                title="Voice Dictation"
              >
                <Mic className={`w-3.5 h-3.5 ${isListening ? 'animate-pulse' : ''}`} />
              </button>

              <div className="w-px h-4 bg-slate-200 dark:bg-slate-800 mx-1" />

              {/* Instant Ask AI selection popover trigger */}
              <button
                onClick={() => {
                  const textarea = textareaRef.current;
                  if (textarea) {
                    const sel = textarea.value.substring(textarea.selectionStart, textarea.selectionEnd);
                    if (sel) {
                      promptAISpawn(`Process this text block: ${sel}`);
                    } else {
                      promptAISpawn("Brainstorm an expansion based on our document.");
                    }
                  }
                }}
                className="p-1.5 rounded-lg hover:bg-indigo-50 dark:hover:bg-indigo-950/20 text-indigo-500 flex items-center gap-1 text-[10px] font-bold"
                title="Query custom prompt with AI"
              >
                <Sparkles className="w-3.5 h-3.5 animate-pulse" /> Ask Copilot AI
              </button>
            </div>
          )}

          {/* Core Textareas or Rendered document */}
          <div className="flex-1 flex flex-col min-h-0">
            {viewMode === 'preview' ? (
              <div 
                className={`markdown-preview animate-fade-in custom-scrollbar overflow-y-auto text-sm leading-relaxed
                  ${theme === 'dark' ? 'text-slate-200' : 'text-slate-800'}`}
                dangerouslySetInnerHTML={{ __html: getRenderedHTML(note.content) }}
              />
            ) : (
              <textarea
                ref={textareaRef}
                placeholder="Click here and start recording your notes. Supports rich Markdown notation (# Headings, **bolds**, - list bullets)..."
                disabled={note.trashed}
                value={note.content}
                onChange={(e) => onUpdateNote(note.id, { content: e.target.value })}
                className={`flex-1 w-full text-sm leading-relaxed focus:outline-none resize-none bg-transparent select-text custom-scrollbar
                  ${theme === 'dark' ? 'placeholder-slate-800 text-slate-200' : 'placeholder-slate-300'}`}
              />
            )}
          </div>

          {/* Dynamic note action panel triggers for selected templates */}
          {!note.trashed && viewMode !== 'preview' && (
            <div className="flex flex-wrap gap-1.5 p-3 rounded-xl bg-slate-50 dark:bg-slate-900/10 border dark:border-slate-900 select-none">
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 p-1 mr-1">AI Polish:</span>
              <button
                onClick={() => onQuickAction('summarize')}
                className="px-2.5 py-1 text-[10.5px] font-semibold rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-600 dark:bg-indigo-950/25 dark:hover:bg-indigo-950/50 dark:text-indigo-400 border border-indigo-100/50 dark:border-indigo-900/30 transition-all"
              >
                Extract Key Points
              </button>
              <button
                onClick={() => onQuickAction('improve')}
                className="px-2.5 py-1 text-[10.5px] font-semibold rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 dark:bg-slate-800/80 dark:hover:bg-slate-800 dark:text-slate-300 transition-all"
              >
                Enhance Writing Flow
              </button>
              <button
                onClick={() => onQuickAction('fix')}
                className="px-2.5 py-1 text-[10.5px] font-semibold rounded-lg bg-teal-50 hover:bg-teal-100 text-teal-600 dark:bg-teal-950/25 dark:hover:bg-teal-950/40 dark:text-teal-400 border border-teal-100/50 dark:border-teal-900/20 transition-all"
              >
                Correct Grammar
              </button>
              <button
                onClick={() => onQuickAction('translate')}
                className="px-2.5 py-1 text-[10.5px] font-semibold rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 dark:bg-slate-800/80 dark:hover:bg-slate-800 dark:text-slate-300 transition-all"
              >
                Translate to Spanish
              </button>
              <button
                onClick={() => onQuickAction('todos')}
                className="px-2.5 py-1 text-[10.5px] font-semibold rounded-lg bg-orange-50 hover:bg-orange-100 text-orange-600 dark:bg-orange-950/25 dark:hover:bg-orange-950/40 dark:text-orange-400 border border-orange-100/50 dark:border-orange-900/20 transition-all"
              >
                Extract Tasks
              </button>
            </div>
          )}

          {/* Note Tags / Labels Input (except trashed notes) */}
          {!note.trashed && (
            <div className={`pt-4 border-t flex flex-wrap items-center gap-2 select-none shrink-0
              ${theme === 'dark' ? 'border-slate-900' : 'border-slate-100'}`}>
              <Tag className="w-4 h-4 text-slate-400" />
              
              {/* Added Tags chips */}
              {note.tags.map((tag) => (
                <span 
                  key={tag} 
                  className="px-2 py-0.5 rounded-full bg-slate-100 hover:bg-red-50 text-slate-600 border dark:bg-slate-900 dark:border-slate-800 dark:hover:bg-red-950/20 dark:text-slate-300 text-[10px] font-bold transition-all flex items-center gap-1 group/chip cursor-pointer"
                  onClick={() => handleRemoveTag(tag)}
                  title="Remove label tag"
                >
                  <span>#{tag}</span>
                  <span className="text-slate-400 group-hover/chip:text-red-500 font-semibold transition-colors">×</span>
                </span>
              ))}

              <form onSubmit={handleTagSubmit} className="flex">
                <input
                  type="text"
                  placeholder="+ Add tag..."
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  className="bg-transparent border-none text-[11px] font-bold text-slate-500 focus:outline-none w-20 py-0.5"
                />
              </form>
            </div>
          )}

          {/* Stat Bar footer metrics */}
          <div className="border-t dark:border-slate-900 border-slate-100 flex flex-col gap-1.5 pt-2 mt-2">
            <footer className="flex items-center justify-between text-[11px] font-semibold text-slate-400 select-none shrink-0">
              <div className="flex gap-4 items-center flex-wrap">
                <span>{note.wordCount} words</span>
                <span>{note.charCount} characters</span>
                <span className="flex items-center gap-1.5"><Clock className="w-3.5 h-3.5" /> {Math.max(1, Math.ceil(note.wordCount / 200))} min read</span>

                {/* Word Count Goal Feature */}
                <div className="flex items-center gap-2 border-l pl-4 border-slate-200 dark:border-slate-800">
                  {isEditingGoal ? (
                    <form onSubmit={handleSaveGoal} className="flex items-center gap-1.5">
                      <Target className="w-3.5 h-3.5 text-indigo-500" />
                      <input
                        type="number"
                        min="1"
                        autoFocus
                        value={goalInput}
                        onChange={(e) => setGoalInput(e.target.value)}
                        onBlur={handleSaveGoal}
                        className="w-16 bg-transparent border-b border-indigo-300 dark:border-indigo-700 text-[11px] font-bold text-slate-700 dark:text-slate-300 focus:outline-none"
                        placeholder="Goal..."
                      />
                    </form>
                  ) : note.targetWordCount ? (
                    <button onClick={() => { setGoalInput(note.targetWordCount?.toString() || ''); setIsEditingGoal(true); }} className="flex items-center gap-1.5 hover:text-indigo-500 transition-colors" title="Edit Goal">
                      <Target className="w-3.5 h-3.5" />
                      <span className={note.wordCount >= note.targetWordCount ? "text-emerald-500 font-bold" : ""}>
                        {note.wordCount} / {note.targetWordCount} goal
                      </span>
                    </button>
                  ) : (
                    <button onClick={() => { setGoalInput(''); setIsEditingGoal(true); }} className="flex items-center gap-1.5 hover:text-indigo-500 transition-colors opacity-60 hover:opacity-100">
                      <Target className="w-3.5 h-3.5" />
                      <span>Set Goal</span>
                    </button>
                  )}
                </div>
              </div>
              
              <div className="text-slate-500 hidden sm:block">
                ID: <code className="text-[10px] font-mono select-all bg-slate-50 dark:bg-slate-900 px-1 py-0.5 rounded">{note.id.slice(0, 8)}</code>
              </div>
            </footer>

            {/* Goal Progress Bar */}
            {note.targetWordCount && note.targetWordCount > 0 && (
              <div className="w-full h-1 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden mt-0.5">
                <div 
                  className={`h-full transition-all duration-500 ${note.wordCount >= note.targetWordCount ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-indigo-500'}`}
                  style={{ width: `${Math.min(100, Math.max(0, (note.wordCount / note.targetWordCount) * 100))}%` }}
                />
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
};

// Simple Fallback icon helper components
const BookOpenIcon = () => (
  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
  </svg>
);
