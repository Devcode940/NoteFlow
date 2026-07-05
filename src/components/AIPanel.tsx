import React, { useState, useRef, useEffect } from 'react';
import { 
  Sparkles, 
  Send, 
  Trash2, 
  Plus, 
  Copy, 
  Check, 
  Loader2, 
  Compass, 
  ArrowRight, 
  FileCheck, 
  CornerDownLeft,
  X,
  AlertCircle
} from 'lucide-react';
import { AIMessage, Note, GoogleUser } from '../types';

interface AIPanelProps {
  note: Note | null;
  onUpdateNote: (id: string, updates: Partial<Note>) => void;
  onCreateNewNoteWithContent: (title: string, content: string) => void;
  history: AIMessage[];
  setHistory: React.Dispatch<React.SetStateAction<AIMessage[]>>;
  theme: 'light' | 'dark';
  onClose: () => void;
  externalPrompt?: string;
  clearExternalPrompt?: () => void;
  googleUser?: GoogleUser | null;
  onSignInTrigger?: (callback?: () => void) => void;
}

export const AIPanel: React.FC<AIPanelProps> = ({
  note,
  onUpdateNote,
  onCreateNewNoteWithContent,
  history,
  setHistory,
  theme,
  onClose,
  externalPrompt,
  clearExternalPrompt,
  googleUser,
  onSignInTrigger
}) => {
  const [chatInput, setChatInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [insertedId, setInsertedId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto scroll messages to bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [history, loading]);

  // Handle external prompt spawn trigger (from edit actions or double-clicks)
  useEffect(() => {
    if (externalPrompt) {
      setChatInput(externalPrompt);
      if (clearExternalPrompt) clearExternalPrompt();
    }
  }, [externalPrompt]);

  const handleSendChat = async (textToSend?: string) => {
    const promptText = (textToSend || chatInput).trim();
    if (!promptText) return;

    const executeChat = async () => {
      if (!textToSend) setChatInput('');
      setErrorMessage('');

      const userMsg: AIMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: promptText,
        timestamp: Date.now()
      };

      const updatedHistory = [...history, userMsg];
      setHistory(updatedHistory);
      setLoading(true);

      try {
        // Gather active note context to supply the LLM
        const contextStr = note 
          ? `Note Title: "${note.title}"\nNote Content:\n"""\n${note.content}\n"""` 
          : "No active note page currently selected.";

        const response = await fetch('/api/ai/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            messages: updatedHistory,
            systemContext: contextStr
          })
        });

        const data = await response.json();
        if (!response.ok) {
          throw new Error(data.error || "Failed to communicate with the AI Server.");
        }

        const assistantMsg: AIMessage = {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: data.content,
          timestamp: Date.now()
        };

        setHistory(prev => [...prev, assistantMsg]);
      } catch (err: any) {
        console.error(err);
        setErrorMessage(err.message || "An unexpected network error occurred.");
      } finally {
        setLoading(false);
      }
    };

    if (googleUser) {
      executeChat();
    } else if (onSignInTrigger) {
      onSignInTrigger(executeChat);
    } else {
      executeChat();
    }
  };

  // Preset Generation actions
  const handlePresetGenerate = async (presetType: 'outline' | 'draft' | 'ideas' | 'continue') => {
    setErrorMessage('');
    
    let userPrompt = '';
    if (presetType === 'outline') {
      userPrompt = `Generate a structured outlines hierarchy for a topic like: "${note?.title || 'a new research project'}"`;
    } else if (presetType === 'draft') {
      userPrompt = `Write an academic draft section or structured letter template regarding: "${note?.title || 'my meeting summary'}"`;
    } else if (presetType === 'ideas') {
      userPrompt = `Give me 10 brainstorming angles or highly actionable ideas for: "${note?.title || 'creative document formats'}"`;
    } else if (presetType === 'continue') {
      if (!note || !note.content.trim()) {
        setErrorMessage("Please type some existing lines in your note so the AI can continue writing from context!");
        return;
      }
      userPrompt = `Continue writing the active document logically and style-match the current paragraph.`;
    }

    const executePreset = () => {
      setChatInput(userPrompt);
      handleSendChat(userPrompt);
    };

    if (googleUser) {
      executePreset();
    } else if (onSignInTrigger) {
      onSignInTrigger(executePreset);
    } else {
      executePreset();
    }
  };

  // Note Insertion modifiers
  const handleInsertIntoNoteContent = (text: string, append = true) => {
    if (!note) {
      // Prompt creation of a new note if none active
      onCreateNewNoteWithContent("AI Generated Document", text);
      return;
    }

    const currentText = note.content;
    const newText = append 
      ? `${currentText}\n\n### AI Copilot Contribution\n${text}`
      : `${text}\n\n${currentText}`;

    onUpdateNote(note.id, { content: newText });
    
    // Quick success animation indicator
    setInsertedId(note.id);
    setTimeout(() => setInsertedId(null), 1500);
  };

  const handleOverwriteNoteContent = (text: string) => {
    if (!note) return;
    if (confirm("Are you sure you want to overwrite the entire active note contents with this AI generated text?")) {
      onUpdateNote(note.id, { content: text });
      setInsertedId(note.id);
      setTimeout(() => setInsertedId(null), 1500);
    }
  };

  const handleCopyText = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleClearHistory = () => {
    if (confirm("Do you want to clear your AI companion session history?")) {
      setHistory([]);
    }
  };

  return (
    <div className={`w-full md:w-96 border-l flex flex-col h-full shrink-0 select-none z-10
      ${theme === 'dark' 
        ? 'bg-slate-900 border-slate-800 text-slate-100' 
        : 'bg-slate-50 border-slate-200 text-slate-800'}`}
    >
      {/* AI Panel Header */}
      <div className={`h-16 px-4 border-b flex items-center justify-between shrink-0
        ${theme === 'dark' ? 'border-slate-800' : 'border-slate-100'}`}>
        <div className="flex items-center gap-2">
          <div className="p-1 rounded bg-indigo-600 text-white animate-pulse">
            <Sparkles className="w-4 h-4 text-indigo-100" />
          </div>
          <span className="font-bold text-sm bg-gradient-to-r from-indigo-500 to-violet-600 bg-clip-text text-transparent">Gemini AI Copilot</span>
        </div>

        <div className="flex items-center gap-1">
          {history.length > 0 && (
            <button
              onClick={handleClearHistory}
              className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400 hover:text-red-500 transition-colors"
              title="Clear conversation history"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400"
            title="Close Assistant Panel"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main Messages list area */}
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4 custom-scrollbar">
        
        {/* Intro greeting card when chat is empty */}
        {history.length === 0 && (
          <div className="py-6 space-y-5 animate-fade-in select-none">
            <div className="p-4 rounded-xl bg-white dark:bg-slate-800/50 border dark:border-slate-800 space-y-2.5 shadow-sm text-center">
              <Sparkles className="w-7 h-7 text-indigo-500 fill-indigo-100 mx-auto" />
              <h4 className="text-xs font-bold text-slate-800 dark:text-slate-200">How can I help with your writing?</h4>
              <p className="text-[10.5px] text-slate-400 dark:text-slate-500 leading-relaxed max-w-xs mx-auto">
                I am connected dynamically to your active note workspace on the left. Type custom cues or pick a quick generation template below to seed documents instantly!
              </p>
            </div>

            {/* Quick Presets Options Grid */}
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 flex items-center gap-1">
                <Compass className="w-3.5 h-3.5 text-slate-400" /> Co-Writing Templates
              </p>

              <div className="grid grid-cols-2 gap-2 text-left">
                <button
                  onClick={() => handlePresetGenerate('outline')}
                  className="p-2.5 rounded-xl bg-white hover:bg-slate-100 border dark:bg-slate-800/40 dark:border-slate-800 dark:hover:bg-slate-800 hover:border-slate-300 text-left transition-all group flex flex-col gap-1"
                >
                  <span className="text-[11px] font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 flex items-center justify-between">
                    Draft Outline <ArrowRight className="w-3 h-3 text-slate-300 group-hover:translate-x-1 transition-transform" />
                  </span>
                  <span className="text-[9.5px] text-slate-400">Generate nested markdown outline structures</span>
                </button>

                <button
                  onClick={() => handlePresetGenerate('ideas')}
                  className="p-2.5 rounded-xl bg-white hover:bg-slate-100 border dark:bg-slate-800/40 dark:border-slate-800 dark:hover:bg-slate-800 hover:border-slate-300 text-left transition-all group flex flex-col gap-1"
                >
                  <span className="text-[11px] font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 flex items-center justify-between">
                    Brainstorming Ideas <ArrowRight className="w-3 h-3 text-slate-300 group-hover:translate-x-1 transition-transform" />
                  </span>
                  <span className="text-[9.5px] text-slate-400">Produce 10 creative insights, angles, or headers</span>
                </button>

                <button
                  onClick={() => handlePresetGenerate('draft')}
                  className="p-2.5 rounded-xl bg-white hover:bg-slate-100 border dark:bg-slate-800/40 dark:border-slate-800 dark:hover:bg-slate-800 hover:border-slate-300 text-left transition-all group flex flex-col gap-1"
                >
                  <span className="text-[11px] font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 flex items-center justify-between">
                    Initial Drafts <ArrowRight className="w-3 h-3 text-slate-300 group-hover:translate-x-1 transition-transform" />
                  </span>
                  <span className="text-[9.5px] text-slate-400">Write high quality paragraphs on titles</span>
                </button>

                <button
                  onClick={() => handlePresetGenerate('continue')}
                  className="p-2.5 rounded-xl bg-white hover:bg-slate-100 border dark:bg-slate-800/40 dark:border-slate-800 dark:hover:bg-slate-800 hover:border-slate-300 text-left transition-all group flex flex-col gap-1"
                  disabled={!note}
                >
                  <span className="text-[11px] font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 flex items-center justify-between">
                    Continue Text <ArrowRight className="w-3 h-3 text-slate-300 group-hover:translate-x-1 transition-transform" style={{ opacity: note ? 1 : 0.3 }} />
                  </span>
                  <span className="text-[9.5px] text-slate-400">Auto-Complete writing from notebook context</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Existing Messages list log */}
        {history.map((msg) => {
          const isUser = msg.role === 'user';
          return (
            <div 
              key={msg.id} 
              className={`flex flex-col gap-1 animate-fade-in ${isUser ? 'items-end' : 'items-start'}`}
            >
              <div className={`p-3 rounded-2xl text-xs leading-relaxed overflow-x-auto max-w-[85%] select-text shadow-sm
                ${isUser 
                  ? 'bg-indigo-600 text-white rounded-br-sm font-medium' 
                  : 'bg-white dark:bg-slate-800 hover:shadow border dark:border-slate-800 text-slate-800 dark:text-slate-100 rounded-bl-sm space-y-2'}`}
              >
                {/* Render Text contents with breaks (and light formatting for ease) */}
                <div className="whitespace-pre-wrap break-words font-sans">
                  {msg.content}
                </div>

                {/* Insertion Utilities inside Assistant Message blocks */}
                {!isUser && (
                  <div className="mt-3.5 pt-2 border-t border-slate-100 dark:border-slate-700/60 flex flex-wrap items-center gap-1.5 text-[9.5px] font-bold select-none text-slate-400">
                    <button
                      onClick={() => handleCopyText(msg.content, msg.id)}
                      className="p-1 px-1.5 rounded bg-slate-50 dark:bg-slate-900 border dark:border-slate-700 hover:bg-slate-100 flex items-center gap-1"
                      title="Copy raw markdown output"
                    >
                      {copiedId === msg.id ? (
                        <><Check className="w-3 h-3 text-emerald-500 shrink-0" /> Copied!</>
                      ) : (
                        <><Copy className="w-3 h-3 shrink-0" /> Copy</>
                      )}
                    </button>

                    <button
                      onClick={() => handleInsertIntoNoteContent(msg.content, true)}
                      className="p-1 px-1.5 rounded bg-indigo-50 hover:bg-indigo-100 dark:bg-indigo-950/20 text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 border border-indigo-100/30 dark:border-indigo-900/40 flex items-center gap-1"
                      title={note ? "Append this content to the bottom of the current note page" : "Create new note page"}
                    >
                      <Plus className="w-3 h-3 shrink-0" /> {note ? "Append" : "Draft New"}
                    </button>

                    {note && (
                      <button
                        onClick={() => handleOverwriteNoteContent(msg.content)}
                        className="p-1 px-1.5 rounded bg-red-50 hover:bg-red-100 text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/30 dark:border-red-900/40 flex items-center gap-1"
                        title="Replace All active note content with this block"
                      >
                        <FileCheck className="w-3 h-3 shrink-0" /> Overwrite
                      </button>
                    )}
                  </div>
                )}
              </div>
              
              <span className="text-[9px] text-slate-400 dark:text-slate-500 px-1 font-semibold">
                {isUser ? 'Me' : 'Gemini AI'} • {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>
          );
        })}

        {/* Loading state streamer animation */}
        {loading && (
          <div className="flex flex-col gap-1 items-start animate-fade-in select-none">
            <div className="p-3 bg-white dark:bg-slate-800 rounded-2xl rounded-bl-sm border dark:border-slate-800 text-xs shadow-sm flex items-center gap-2">
              <Loader2 className="w-3.5 h-3.5 text-indigo-500 animate-spin" />
              <span className="text-slate-400 font-semibold animate-pulse">Gemini writing...</span>
            </div>
          </div>
        )}

        {/* Error alert widget */}
        {errorMessage && (
          <div className="p-3 bg-red-50 dark:bg-red-950/25 border border-red-100 dark:border-red-900/40 rounded-xl text-xs text-red-600 dark:text-red-400 flex gap-2 animate-fade-in shadow-sm select-text">
            <AlertCircle className="w-4.5 h-4.5 text-red-500 shrink-0" />
            <div className="space-y-1">
              <p className="font-bold">Execution Error</p>
              <p className="leading-relaxed text-[11px]">{errorMessage}</p>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input controls form footer */}
      <div className={`p-4 border-t shrink-0 select-none
        ${theme === 'dark' ? 'border-slate-800 bg-slate-900/55' : 'border-slate-100 bg-white'}`}>
        
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendChat();
          }}
          className="flex gap-2"
        >
          <input
            type="text"
            placeholder={note ? `Ask Copilot about "${note.title.slice(0, 15)}..."` : "Draft writing instruction..."}
            value={chatInput}
            onChange={(e) => setChatInput(e.target.value)}
            disabled={loading}
            className={`flex-1 px-3 py-2 rounded-xl text-xs border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 select-text
              ${theme === 'dark' 
                ? 'bg-slate-800 border-slate-700 text-white placeholder-slate-500' 
                : 'bg-slate-50 border-slate-200 text-slate-800 placeholder-slate-400'}`}
          />
          <button
            type="submit"
            disabled={loading || !chatInput.trim()}
            className="p-2 rounded-xl bg-indigo-600 text-white disabled:bg-slate-200 dark:disabled:bg-slate-800 text-xs font-semibold hover:bg-indigo-700 transition-colors shadow-sm shrink-0 flex items-center justify-center min-w-8"
          >
            <Send className="w-3.5 h-3.5" />
          </button>
        </form>

        <p className="text-[10px] text-slate-400 dark:text-slate-500 text-center mt-2 font-medium">
          Gemini 2.5-flash Grounded Model • Chats are context-aware
        </p>
      </div>
    </div>
  );
};
