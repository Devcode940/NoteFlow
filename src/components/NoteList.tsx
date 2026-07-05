import React from 'react';
import { 
  Search, 
  ChevronDown, 
  ChevronUp, 
  Star, 
  Archive, 
  Plus, 
  Calendar,
  Tag,
  FolderOpen
} from 'lucide-react';
import { motion, type Variants } from 'motion/react';
import { Note, Folder, SortBy, SortOrder, DEFAULT_FOLDERS } from '../types';

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.04,
      delayChildren: 0.02
    }
  }
};

const itemVariants: Variants = {
  hidden: { y: 12, opacity: 0 },
  show: { 
    y: 0, 
    opacity: 1,
    transition: {
      type: "spring",
      stiffness: 110,
      damping: 14
    }
  }
};

interface NoteListProps {
  notes: Note[];
  folders: Folder[];
  activeNoteId: string | null;
  setActiveNoteId: (id: string | null) => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  activeFolder: string;
  activeTag: string | null;
  sortBy: SortBy;
  setSortBy: (field: SortBy) => void;
  sortOrder: SortOrder;
  setSortOrder: (order: SortOrder) => void;
  onCreateNote: () => void;
  onTogglePin: (id: string, e: React.MouseEvent) => void;
  onMoveNote: (id: string, folderId: string) => void;
  allFolders: Folder[];
  theme: 'light' | 'dark';
}

export const NoteList: React.FC<NoteListProps> = ({
  notes,
  folders,
  activeNoteId,
  setActiveNoteId,
  searchQuery,
  setSearchQuery,
  activeFolder,
  activeTag,
  sortBy,
  setSortBy,
  sortOrder,
  setSortOrder,
  onCreateNote,
  onTogglePin,
  onMoveNote,
  allFolders,
  theme
}) => {
  // Find current active folder details
  const folderDetails = folders.find(f => f.id === activeFolder);

  // Filter notes based on folder selection
  let filteredNotes = notes.filter(note => {
    // Trash folder logic
    if (activeFolder === 'trash') {
      return note.trashed;
    }
    // Anything in active trash goes there, so exclude from other filters
    if (note.trashed) return false;

    // Archive folder logic
    if (activeFolder === 'archive') {
      return note.archived;
    }
    // Exclude archived notes from general lists unless explicitly browsing archive
    if (note.archived) return false;

    // Favorites folder logic
    if (activeFolder === 'favorites') {
      return note.pinned;
    }

    // All notes folder logic
    if (activeFolder === 'all') {
      return true;
    }

    // Specific custom / preset workspace folder
    return note.folderId === activeFolder;
  });

  // Filter notes based on active tag label
  if (activeTag) {
    filteredNotes = filteredNotes.filter(note => 
      note.tags.map(t => t.toLowerCase()).includes(activeTag.toLowerCase())
    );
  }

  // Filter notes based on search text (query match title or body)
  if (searchQuery.trim()) {
    const term = searchQuery.toLowerCase();
    filteredNotes = filteredNotes.filter(note => 
      note.title.toLowerCase().includes(term) || 
      note.content.toLowerCase().includes(term)
    );
  }

  // Sort notes
  const sortedNotes = [...filteredNotes].sort((a, b) => {
    let output = 0;
    if (sortBy === 'title') {
      output = a.title.localeCompare(b.title);
    } else if (sortBy === 'createdAt') {
      output = a.createdAt - b.createdAt;
    } else if (sortBy === 'updatedAt') {
      output = a.updatedAt - b.updatedAt;
    }

    return sortOrder === 'desc' ? -output : output;
  });

  // Formatter for timestamp
  const formatTime = (ts: number) => {
    const d = new Date(ts);
    const now = new Date();
    
    // Check if today
    if (d.toDateString() === now.toDateString()) {
      return `Today at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }
    
    // Check if yesterday
    const yesterday = new Date();
    yesterday.setDate(now.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) {
      return `Yesterday at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }

    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  // Safe Snippet extract
  const getContentSnippet = (txt: string) => {
    // Strip markdown formatting simple approach
    const stripped = txt
      .replace(/[#*`_\[\]()=\-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    return stripped.length > 70 ? stripped.slice(0, 70) + '...' : stripped || 'No content';
  };

  // Search Match Highlighter
  const highlightText = (text: string, search: string) => {
    if (!search || !text) return <>{text}</>;
    const index = text.toLowerCase().indexOf(search.toLowerCase());
    if (index === -1) return <>{text}</>;
    
    const before = text.slice(0, index);
    const match = text.slice(index, index + search.length);
    const after = text.slice(index + search.length);

    return (
      <>
        {before}
        <mark className="bg-yellow-100 text-slate-900 rounded-sm px-0.5 dark:bg-yellow-400/35 dark:text-yellow-100">{match}</mark>
        {after}
      </>
    );
  };

  return (
    <div className={`w-full md:w-80 h-full border-r flex flex-col z-10 select-none
      ${theme === 'dark' 
        ? 'bg-slate-900/40 border-slate-800 text-slate-100' 
        : 'bg-slate-50 border-slate-200 text-slate-800'}`}
    >
      {/* Search Bar Entry */}
      <div className="p-4 flex flex-col gap-3 border-b border-transparent">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5 truncate">
            <span className="text-base shrink-0">{folderDetails?.icon || '📁'}</span>
            <span className="font-bold text-sm tracking-tight text-slate-800 dark:text-white truncate">
              {folderDetails?.name || 'Browse Workspace'} 
              <span className="ml-1 text-slate-400 font-normal">({sortedNotes.length})</span>
            </span>
          </div>

          <button
            onClick={onCreateNote}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 transition-colors shadow-sm shrink-0"
            title="Create Note (Ctrl+N)"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Note</span>
          </button>
        </div>

        {/* Searching field */}
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search notes (Ctrl+F)"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={`w-full pl-9 pr-3 py-2 rounded-lg text-xs border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500
              ${theme === 'dark' 
                ? 'bg-slate-800/80 border-slate-700 text-white' 
                : 'bg-white border-slate-200'}`}
          />
        </div>

        {/* Sorting controls */}
        <div className="flex items-center justify-between text-[11px] text-slate-400 dark:text-slate-500 font-medium">
          <div className="flex items-center gap-1.5">
            <span>Sort:</span>
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortBy)}
              className="bg-transparent border-none text-slate-500 dark:text-slate-300 focus:outline-none pr-3 cursor-pointer"
            >
              <option value="updatedAt">Edited</option>
              <option value="createdAt">Created</option>
              <option value="title">Alphabetical</option>
            </select>
          </div>

          <button
            onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')}
            className="flex items-center hover:text-indigo-500 transition-colors gap-0.5"
            title={sortOrder === 'asc' ? 'Ascending File order' : 'Descending File order'}
          >
            <span>Order</span>
            {sortOrder === 'asc' ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>

      {/* Dynamic Tag Identifier bar if tag active */}
      {activeTag && (
        <div className="mx-4 mt-2 px-3 py-1.5 rounded-lg bg-indigo-50 dark:bg-indigo-950/20 text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 flex justify-between items-center animate-fade-in shrink-0">
          <span className="flex items-center gap-1"><Tag className="w-3.5 h-3.5" /> Tagged: #{activeTag}</span>
        </div>
      )}

      {/* Notes List Scrolling */}
      <motion.div
        key={`${activeFolder}-${activeTag || 'all'}`}
        variants={containerVariants}
        initial="hidden"
        animate="show"
        className="flex-1 overflow-y-auto px-2 py-3 space-y-2 custom-scrollbar"
      >
        {sortedNotes.length > 0 ? (
          sortedNotes.map((note) => {
            const isSelected = activeNoteId === note.id;
            const originalFolder = allFolders.find(f => f.id === note.folderId);

            return (
              <motion.div
                key={note.id}
                variants={itemVariants}
                onClick={() => setActiveNoteId(note.id)}
                className={`p-3 rounded-xl border text-left cursor-pointer transition-all duration-200 relative group flex flex-col gap-1.5 select-none
                  ${isSelected
                    ? 'bg-white border-indigo-500 shadow-md dark:bg-slate-800 dark:border-indigo-400'
                    : 'bg-white/40 border-transparent hover:bg-white dark:bg-transparent dark:hover:bg-slate-800/20'}`}
              >
                {/* Note Header Title */}
                <div className="flex items-start justify-between gap-1">
                  <span className={`font-bold text-xs tracking-tight truncate flex-1
                    ${isSelected 
                      ? 'text-indigo-600 dark:text-indigo-400' 
                      : 'text-slate-700 dark:text-slate-200'}`}
                  >
                    {highlightText(note.title || 'Untitled note', searchQuery)}
                  </span>

                  {/* Pin button on card */}
                  {!note.trashed && (
                    <button
                      onClick={(e) => onTogglePin(note.id, e)}
                      className={`p-0.5 rounded hover:bg-amber-50 dark:hover:bg-amber-950/20 transition-colors
                        ${note.pinned 
                          ? 'opacity-100 text-amber-500' 
                          : 'opacity-0 group-hover:opacity-100 text-slate-300 hover:text-amber-500'}`}
                    >
                      <Star className="w-3.5 h-3.5 fill-current" />
                    </button>
                  )}
                </div>

                {/* Content teaser lines */}
                <p className="text-[11px] leading-relaxed text-slate-400 dark:text-slate-500 line-clamp-2">
                  {highlightText(getContentSnippet(note.content), searchQuery)}
                </p>

                {/* Footer Metadata */}
                <div className="flex items-center justify-between border-t border-slate-100 dark:border-slate-800/80 pt-2 text-[10px] font-medium text-slate-400 dark:text-slate-500 shrink-0">
                  <div className="flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    <span>{formatTime(note.updatedAt)}</span>
                  </div>

                  {/* Icons for states */}
                  <div className="flex items-center gap-1.5">
                    {note.archived && <Archive className="w-3 h-3 text-teal-500" />}
                    
                    {/* Folder label */}
                    {originalFolder && originalFolder.id !== 'all' && (
                      <span className="flex items-center gap-0.5 truncate max-w-20 bg-slate-100 dark:bg-slate-800 px-1 rounded text-slate-500 py-0.5 shrink-0">
                        <span>{originalFolder.icon}</span>
                        <span className="truncate">{originalFolder.name}</span>
                      </span>
                    )}

                    {/* Simple Folder Mover for quick relocation */}
                    {!note.trashed && !note.archived && allFolders.length > DEFAULT_FOLDERS.length && (
                      <div className="relative inline-block opacity-0 group-hover:opacity-100 transition-opacity">
                        <select
                          onClick={(e) => e.stopPropagation()}
                          onChange={(e) => {
                            e.stopPropagation();
                            onMoveNote(note.id, e.target.value);
                          }}
                          value={note.folderId}
                          title="Move Note Notebook"
                          className="absolute inset-0 opacity-0 cursor-pointer w-4 h-4"
                        >
                          <option disabled value="">Move to...</option>
                          {allFolders.filter(f => f.id !== 'all' && f.id !== 'favorites').map((fol) => (
                            <option key={fol.id} value={fol.id}>
                              {fol.icon} {fol.name}
                            </option>
                          ))}
                        </select>
                        <FolderOpen className="w-3.5 h-3.5 text-slate-400 hover:text-indigo-500" />
                      </div>
                    )}
                  </div>
                </div>
              </motion.div>
            );
          })
        ) : (
          <div className="py-16 text-center text-slate-400 dark:text-slate-500 select-none animate-fade-in flex flex-col items-center justify-center p-4">
            <span className="text-3xl mb-2">🎈</span>
            <p className="text-xs font-semibold">No notes in this category</p>
            <p className="text-[10px] text-slate-400/80 max-w-44 mt-1 leading-relaxed">
              Create a fresh note or reset tags filtering to continue
            </p>
          </div>
        )}
      </motion.div>
    </div>
  );
};
