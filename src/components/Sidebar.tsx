import React, { useState } from 'react';
import { 
  Folder, 
  FolderPlus, 
  Tag, 
  Settings, 
  BookOpen, 
  Moon, 
  Sun, 
  ChevronLeft, 
  ChevronRight, 
  Trash2, 
  Sparkles,
  Info,
  X,
  Keyboard,
  LogOut,
  Cloud,
  CheckCircle2
} from 'lucide-react';
import { Folder as FolderType, Theme, GoogleUser } from '../types';

interface SidebarProps {
  folders: FolderType[];
  activeFolder: string;
  setActiveFolder: (id: string) => void;
  activeTag: string | null;
  setActiveTag: (tag: string | null) => void;
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
  theme: Theme;
  toggleTheme: () => void;
  allTags: string[];
  onCreateFolder: (name: string, icon: string, color: string) => void;
  onDeleteFolder: (id: string) => void;
  googleUser?: GoogleUser | null;
  onSignOut?: () => void;
  onSignInTrigger?: () => void;
}


const PRESET_COLORS = [
  '#7c3aed', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', 
  '#f97316', '#ec4899', '#14b8a6', '#64748b'
];

const PRESET_EMOJIS = ['📋', '🏠', '💼', '💡', '📌', '🚀', '🎨', '📝', '🧘‍♂️', '🎯', '🥑', '⚡️', '🌟'];

export const Sidebar: React.FC<SidebarProps> = ({
  folders,
  activeFolder,
  setActiveFolder,
  activeTag,
  setActiveTag,
  collapsed,
  setCollapsed,
  theme,
  toggleTheme,
  allTags,
  onCreateFolder,
  onDeleteFolder,
  googleUser,
  onSignOut,
  onSignInTrigger
}) => {
  const [showNewFolderModal, setShowNewFolderModal] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [newFolderColor, setNewFolderColor] = useState(PRESET_COLORS[0]);
  const [newFolderEmoji, setNewFolderEmoji] = useState(PRESET_EMOJIS[0]);
  const [showShortcutsModal, setShowShortcutsModal] = useState(false);

  // Group default folders vs custom folders
  const defaultFolders = folders.slice(0, 7);
  const customFolders = folders.slice(7);

  const handleCreateFolderSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;
    onCreateFolder(newFolderName.trim(), newFolderEmoji, newFolderColor);
    setNewFolderName('');
    setShowNewFolderModal(false);
  };

  return (
    <aside 
      className={`relative h-full border-r transition-all duration-300 flex flex-col z-20 select-none
        ${collapsed ? 'w-16' : 'w-64'} 
        ${theme === 'dark' 
          ? 'bg-slate-900 border-slate-800 text-slate-100' 
          : 'bg-white border-slate-200 text-slate-800'}`}
    >
      {/* Top Header Logo */}
      <div className="h-16 flex items-center justify-between px-4 border-b border-transparent">
        {!collapsed && (
          <div className="flex items-center gap-2 font-semibold">
            <div className="bg-indigo-600 text-white p-1.5 rounded-lg flex items-center justify-center shadow-sm">
              <Sparkles className="w-5 h-5 text-indigo-100 fill-indigo-200 animate-pulse" />
            </div>
            <span className="text-lg font-bold tracking-tight bg-gradient-to-r from-indigo-500 to-violet-600 bg-clip-text text-transparent">
              NoteFlow AI
            </span>
          </div>
        )}
        {collapsed && (
          <div className="mx-auto bg-indigo-600 text-white p-2 rounded-lg flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-indigo-100 fill-indigo-200" />
          </div>
        )}

        {/* Collapse Button */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className={`p-1 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800/80 text-slate-500 transition-colors
            ${collapsed ? 'absolute -right-3 top-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-md z-30 rounded-full' : ''}`}
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>

      {/* Folders & Navigation Section */}
      <div className="flex-1 overflow-y-auto py-4 px-3 custom-scrollbar space-y-6">
        {/* Core Folders */}
        <div className="space-y-1">
          {!collapsed && (
            <p className="px-2 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 mb-2">
              Bespoke Spaces
            </p>
          )}
          {defaultFolders.map((item) => {
            const isActive = activeFolder === item.id && activeTag === null;
            return (
              <button
                key={item.id}
                onClick={() => {
                  setActiveFolder(item.id);
                  setActiveTag(null);
                }}
                className={`w-full flex items-center gap-3 px-2 py-2 rounded-lg text-sm text-left font-medium transition-all group
                  ${isActive 
                    ? 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-400' 
                    : 'hover:bg-slate-50 dark:hover:bg-slate-800/40 text-slate-600 dark:text-slate-300'}`}
              >
                <span className="text-base" style={{ filter: isActive ? 'none' : 'grayscale(30%)' }}>
                  {item.icon}
                </span>
                {!collapsed && (
                  <span className="truncate flex-1">
                    {item.name}
                  </span>
                )}
                {!collapsed && isActive && (
                  <div className="w-1.5 h-1.5 rounded-full bg-indigo-600 dark:bg-indigo-400" />
                )}
              </button>
            );
          })}
        </div>

        {/* Custom User Folders */}
        <div className="space-y-1">
          <div className="flex items-center justify-between px-2 mb-2">
            {!collapsed && (
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                Custom Notebooks
              </p>
            )}
            {!collapsed && (
              <button
                onClick={() => setShowNewFolderModal(true)}
                className="p-1 rounded text-slate-400 hover:text-indigo-600 dark:text-slate-500 dark:hover:text-indigo-400 transition-colors"
                title="Create Folder"
              >
                <FolderPlus className="w-4 h-4" />
              </button>
            )}
          </div>

          {customFolders.map((item) => {
            const isActive = activeFolder === item.id && activeTag === null;
            return (
              <div
                key={item.id}
                className={`w-full flex items-center justify-between py-1.5 px-2 rounded-lg text-sm font-medium transition-all group
                  ${isActive 
                    ? 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-400' 
                    : 'hover:bg-slate-50 dark:hover:bg-slate-800/40 text-slate-600 dark:text-slate-300'}`}
              >
                <button
                  onClick={() => {
                    setActiveFolder(item.id);
                    setActiveTag(null);
                  }}
                  className="flex items-center gap-3 overflow-hidden text-left flex-1"
                >
                  <span className="text-base" style={{ color: item.color }}>
                    {item.icon}
                  </span>
                  {!collapsed && <span className="truncate">{item.name}</span>}
                </button>
                {!collapsed && (
                  <button
                    onClick={() => onDeleteFolder(item.id)}
                    className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-400 hover:text-red-500 transition-all"
                    title="Delete Notebook"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            );
          })}

          {!collapsed && customFolders.length === 0 && (
            <p className="text-xs text-slate-400 dark:text-slate-500 italic px-2 py-1">
              No custom folders
            </p>
          )}
        </div>

        {/* Labels / Tags Section */}
        <div className="space-y-1">
          {!collapsed && (
            <p className="px-2 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 mb-2">
              Organized tags
            </p>
          )}
          <div className="flex flex-wrap gap-1.5 px-2">
            {!collapsed && allTags.map((tag) => {
              const isTagActive = activeTag === tag;
              return (
                <button
                  key={tag}
                  onClick={() => {
                    if (isTagActive) {
                      setActiveTag(null);
                    } else {
                      setActiveTag(tag);
                    }
                  }}
                  className={`px-2.5 py-1 rounded-full text-xs font-medium transition-all flex items-center gap-1
                    ${isTagActive 
                      ? 'bg-indigo-600 text-white shadow-sm' 
                      : 'bg-slate-100 hover:bg-slate-200 text-slate-600 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-300'}`}
                >
                  <Tag className="w-3 h-3" />
                  <span>{tag}</span>
                </button>
              );
            })}
            {!collapsed && allTags.length === 0 && (
              <p className="text-xs text-slate-400 dark:text-slate-500 italic py-1">
                Add #tags inside notes to index them
              </p>
            )}
            {collapsed && (
              <div className="mx-auto text-slate-400 dark:text-slate-500">
                <Tag className="w-4 h-4 mx-auto" />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Footer Section */}
      <div className={`p-4 border-t ${theme === 'dark' ? 'border-slate-800' : 'border-slate-100'} flex flex-col gap-2`}>
        {/* Google Cloud Auth profile sync */}
        <div className={`p-1.5 rounded-lg border flex items-center transition-all duration-300
          ${theme === 'dark' ? 'border-slate-800/85 bg-slate-900/30' : 'border-slate-200/70 bg-slate-50/40'}`}
        >
          {googleUser ? (
            <div className="flex w-full items-center gap-1.5">
              <div className="relative shrink-0 flex items-center justify-center">
                <img
                  src={googleUser.picture}
                  alt={googleUser.name}
                  referrerPolicy="no-referrer"
                  className="w-7 h-7 rounded-full object-cover border border-indigo-500/20"
                />
                <div className="absolute right-0 bottom-0 w-2 h-2 rounded-full bg-emerald-500 border border-white dark:border-slate-950" />
              </div>
              
              {!collapsed ? (
                <div className="flex-1 min-w-0">
                  <p className="text-[11px] font-bold truncate leading-tight text-slate-700 dark:text-slate-300">{googleUser.name}</p>
                  <span className="text-[9px] font-mono text-slate-400 dark:text-slate-500 leading-none flex items-center gap-0.5 truncate mt-0.5">
                    <CheckCircle2 className="w-2.5 h-2.5 text-emerald-500 shrink-0" />
                    Cloud Sync Active
                  </span>
                </div>
              ) : null}

              {!collapsed && onSignOut ? (
                <button
                  type="button"
                  onClick={onSignOut}
                  className="p-1 rounded-md text-slate-400 hover:text-red-500 hover:bg-red-50/25 dark:hover:bg-red-950/25 transition-colors cursor-pointer"
                  title="Sign Out of Google"
                >
                  <LogOut className="w-3.5 h-3.5" />
                </button>
              ) : null}

              {collapsed && onSignOut ? (
                <button
                  type="button"
                  onClick={onSignOut}
                  className="mx-auto shrink-0 p-0.5 rounded-md text-slate-400 hover:text-red-500 transition-colors cursor-pointer"
                  title="Cloud Active (Click to Sign Out)"
                >
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                </button>
              ) : null}
            </div>
          ) : (
            <button
              onClick={onSignInTrigger}
              className={`w-full flex items-center justify-center gap-2 py-1.5 rounded-md text-[11px] font-semibold transition-all cursor-pointer hover:shadow-sm
                ${theme === 'dark' 
                  ? 'bg-gradient-to-r from-indigo-950/50 to-slate-900 text-indigo-300 border border-indigo-900/30 hover:from-indigo-900/60' 
                  : 'bg-white text-indigo-600 border border-indigo-200/80 hover:bg-indigo-50/40'}`}
              title="Backup with Google Account"
            >
              <Cloud className="w-3.5 h-3.5 animate-pulse text-indigo-500" />
              {!collapsed && <span className="truncate">Cloud Sync Offline</span>}
            </button>
          )}
        </div>

        {/* Toggle Theme */}
        <button
          onClick={toggleTheme}
          className="w-full flex items-center justify-center gap-3 py-2 rounded-lg text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400"
          title={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
        >
          {theme === 'dark' ? (
            <>
              <Sun className="w-4.5 h-4.5 text-amber-400" />
              {!collapsed && <span>Brighter Screen</span>}
            </>
          ) : (
            <>
              <Moon className="w-4.5 h-4.5 text-indigo-500" />
              {!collapsed && <span>Dim Screen</span>}
            </>
          )}
        </button>

        {/* Shortcuts / Information */}
        <button
          onClick={() => setShowShortcutsModal(true)}
          className="w-full flex items-center justify-center gap-3 py-2 rounded-lg text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400"
          title="Keyboard Shortcuts"
        >
          <Keyboard className="w-4.5 h-4.5 text-teal-500" />
          {!collapsed && <span>Hotkeys Setup</span>}
        </button>
      </div>

      {/* New Folder Modal */}
      {showNewFolderModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className={`w-full max-w-sm rounded-xl p-5 shadow-xl animate-fade-in
            ${theme === 'dark' ? 'bg-slate-900 border border-slate-800 text-slate-100' : 'bg-white text-slate-800'}`}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-bold flex items-center gap-2">
                <FolderPlus className="w-4.5 h-4.5 text-indigo-600 dark:text-indigo-400" />
                New Notebook
              </h3>
              <button 
                onClick={() => setShowNewFolderModal(false)}
                className="p-1 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateFolderSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">
                  Notebook Title
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Shopping List, Study Notes"
                  value={newFolderName}
                  onChange={(e) => setNewFolderName(e.target.value)}
                  className={`w-full px-3 py-2 rounded-lg text-sm border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500
                    ${theme === 'dark' ? 'bg-slate-800 border-slate-700 text-white' : 'bg-slate-50 border-slate-200'}`}
                  autoFocus
                />
              </div>

              {/* Emoji Select list */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">
                  Pick Badge Icon
                </label>
                <div className="grid grid-cols-7 gap-1.5">
                  {PRESET_EMOJIS.map((emoji) => (
                    <button
                      key={emoji}
                      type="button"
                      onClick={() => setNewFolderEmoji(emoji)}
                      className={`text-xl p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 border transition-all
                        ${newFolderEmoji === emoji 
                          ? 'border-indigo-600 bg-indigo-50/20 scale-110' 
                          : 'border-transparent'}`}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
              </div>

              {/* Color List */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">
                  Folder Theme Color
                </label>
                <div className="flex flex-wrap gap-2">
                  {PRESET_COLORS.map((color) => (
                    <button
                      key={color}
                      type="button"
                      onClick={() => setNewFolderColor(color)}
                      className={`w-6 h-6 rounded-full border-2 transition-transform hover:scale-110
                        ${newFolderColor === color ? 'border-indigo-600 scale-110 shadow-sm' : 'border-transparent'}`}
                      style={{ backgroundColor: color }}
                    />
                  ))}
                </div>
              </div>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setShowNewFolderModal(false)}
                  className={`flex-1 py-2 text-sm font-medium rounded-lg border transition-colors
                    ${theme === 'dark' 
                      ? 'border-slate-800 hover:bg-slate-800 hover:text-white' 
                      : 'border-slate-200 hover:bg-slate-50'}`}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 text-sm font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition-colors shadow-sm"
                >
                  Craft Notebook
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Keyboard Shortcuts Modal */}
      {showShortcutsModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className={`w-full max-w-md rounded-xl p-6 shadow-xl animate-fade-in
            ${theme === 'dark' ? 'bg-slate-900 border border-slate-800 text-slate-100' : 'bg-white text-slate-800'}`}>
            <div className="flex items-center justify-between mb-5">
              <h3 className="text-base font-bold flex items-center gap-2">
                <Keyboard className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                Performance Shortcuts
              </h3>
              <button 
                onClick={() => setShowShortcutsModal(false)}
                className="p-1 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3.5">
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">New Note</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + N</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Open AI Copilot Helper</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + J</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Save Active Progress</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + S</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Focus on Search</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + F</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Markdown: Bold Words</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + B</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Markdown: Italic Style</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + I</span>
              </div>
              <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100 dark:border-slate-800/80">
                <span className="text-slate-500 dark:text-slate-400">Markdown: Highlight Text</span>
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">Ctrl + H</span>
              </div>
            </div>

            <div className="mt-6 p-3 bg-indigo-50/40 dark:bg-indigo-950/25 border border-indigo-100/50 dark:border-indigo-900/30 rounded-lg flex gap-2.5">
              <Info className="w-5 h-5 text-indigo-500 shrink-0" />
              <p className="text-xs text-indigo-600/95 dark:text-indigo-400/90 leading-relaxed">
                Add <b>#labels</b> anywhere in your note content to dynamically register tag indexes in the left sidebar directory automatically!
              </p>
            </div>

            <button
              onClick={() => setShowShortcutsModal(false)}
              className="mt-5 w-full py-2 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 transition-colors rounded-lg text-sm font-semibold"
            >
              Close Guidelines
            </button>
          </div>
        </div>
      )}
    </aside>
  );
};
