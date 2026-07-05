import React, { useState, useEffect, useRef } from 'react';
import { Search, File, Plus, Sun, Moon, Sparkles } from 'lucide-react';
import { Note, Theme, Folder } from '../types';

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  notes: Note[];
  folders: Folder[];
  onSelectNote: (id: string) => void;
  onCreateNote: () => void;
  theme: Theme;
  onToggleTheme: () => void;
  onAskAI: () => void;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({
  isOpen,
  onClose,
  notes,
  folders,
  onSelectNote,
  onCreateNote,
  theme,
  onToggleTheme,
  onAskAI
}) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setQuery('');
      setSelectedIndex(0);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const validNotes = notes.filter(n => !n.trashed);

  const getFilteredItems = () => {
    const q = query.toLowerCase();
    
    const actions = [
      { id: 'create', title: 'Create new note', icon: Plus, group: 'Actions', subtitle: undefined as string | undefined, onSelect: onCreateNote },
      { id: 'ask', title: 'Ask AI Assistant', icon: Sparkles, group: 'Actions', subtitle: undefined as string | undefined, onSelect: onAskAI },
      { id: 'theme', title: `Toggle ${theme === 'dark' ? 'Light' : 'Dark'} Mode`, icon: theme === 'dark' ? Sun : Moon, group: 'Actions', subtitle: undefined as string | undefined, onSelect: onToggleTheme },
    ].filter(a => a.title.toLowerCase().includes(q));

    const noteItems = validNotes
      .filter(n => n.title.toLowerCase().includes(q) || n.content.toLowerCase().includes(q))
      .slice(0, 5)
      .map(n => ({
        id: `note-${n.id}`, 
        title: n.title || 'Untitled Note', 
        subtitle: folders.find(f => f.id === n.folderId)?.name,
        icon: File, 
        group: 'Notes', 
        onSelect: () => onSelectNote(n.id) 
      }));

    return [...actions, ...noteItems];
  };

  const filteredItems = getFilteredItems();

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => Math.min(prev + 1, filteredItems.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = filteredItems[selectedIndex];
      if (item) {
        item.onSelect();
        onClose();
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-32 sm:pt-48 animate-fade-in px-4">
      <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm" onClick={onClose} />
      
      <div className={`relative w-full max-w-xl rounded-2xl border shadow-2xl flex flex-col overflow-hidden text-left
        ${theme === 'dark' ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200'}`}>
        
        {/* Search Input */}
        <div className="flex items-center px-4 py-4 border-b border-transparent">
          <Search className="w-5 h-5 text-indigo-500 mr-2 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            placeholder="Type a command or search notes..."
            className={`w-full bg-transparent border-none focus:outline-none text-base outline-none
              ${theme === 'dark' ? 'text-white placeholder-slate-500' : 'text-slate-900 placeholder-slate-400'}`}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleKeyDown}
          />
        </div>

        {/* Results Body */}
        {filteredItems.length > 0 ? (
          <div className={`max-h-80 overflow-y-auto px-2 py-2 border-t text-sm select-none custom-scrollbar
            ${theme === 'dark' ? 'border-slate-800' : 'border-slate-100'}`}>
            
            {filteredItems.map((item, index) => {
              const Icon = item.icon;
              const isSelected = index === selectedIndex;
              const previousItem = index > 0 ? filteredItems[index - 1] : null;
              const showGroupHeader = !previousItem || previousItem.group !== item.group;

              return (
                <React.Fragment key={item.id}>
                  {showGroupHeader && (
                    <div className="px-3 py-1.5 mt-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                      {item.group}
                    </div>
                  )}
                  <div
                    onMouseEnter={() => setSelectedIndex(index)}
                    onClick={() => {
                      item.onSelect();
                      onClose();
                    }}
                    className={`flex items-center gap-3 px-3 py-2 rounded-xl cursor-pointer transition-colors
                      ${isSelected 
                        ? (theme === 'dark' ? 'bg-indigo-600/20 text-indigo-100' : 'bg-indigo-50 text-indigo-700') 
                        : (theme === 'dark' ? 'text-slate-300 hover:bg-slate-800/60' : 'text-slate-600 hover:bg-slate-50')}`}
                  >
                    <Icon className={`w-4 h-4 ${isSelected ? 'text-indigo-500' : 'text-slate-400'}`} />
                    <div className="flex flex-col flex-1">
                      <span className="font-semibold text-sm truncate">{item.title}</span>
                      {item.subtitle && (
                        <span className={`text-[10px] truncate ${isSelected ? 'opacity-80' : 'text-slate-500'}`}>
                          In {item.subtitle}
                        </span>
                      )}
                    </div>
                    {isSelected && (
                      <span className="text-[10px] text-indigo-400 font-semibold tracking-wide uppercase shrink-0">Enter</span>
                    )}
                  </div>
                </React.Fragment>
              );
            })}
          </div>
        ) : (
          <div className="py-8 text-center text-sm text-slate-500 bg-slate-50/50 dark:bg-slate-800/20">
            No results found for "{query}"
          </div>
        )}

      </div>
    </div>
  );
};
