import { createClient } from '@supabase/supabase-js';
import { Note, Folder } from '../types';

const supabaseUrl = (import.meta as any).env?.VITE_SUPABASE_URL || '';
const supabaseKey = (import.meta as any).env?.VITE_SUPABASE_ANON_KEY || '';

export const supabase = supabaseUrl && supabaseKey ? createClient(supabaseUrl, supabaseKey) : null;

export const syncNoteToSupabase = async (note: Note) => {
  if (!supabase) return;
  try {
    const { error } = await supabase.from('notes').upsert({
      id: note.id,
      title: note.title,
      content: note.content,
      folderId: note.folderId,
      createdAt: note.createdAt,
      updatedAt: note.updatedAt,
      trashed: note.trashed,
      tags: note.tags,
      wordCount: note.wordCount,
      charCount: note.charCount,
      targetWordCount: note.targetWordCount
    });
    if (error) console.error('Supabase sync error (note):', error);
  } catch (err) {
    console.error('Supabase sync error (note):', err);
  }
};

export const syncFolderToSupabase = async (folder: Folder) => {
  if (!supabase) return;
  try {
    const { error } = await supabase.from('folders').upsert({
      id: folder.id,
      name: folder.name,
      color: folder.color,
      icon: folder.icon,
      parent: folder.parent || null
    });
    if (error) console.error('Supabase sync error (folder):', error);
  } catch (err) {
    console.error('Supabase sync error (folder):', err);
  }
};

export const deleteNoteFromSupabase = async (id: string) => {
  if (!supabase) return;
  try {
    await supabase.from('notes').delete().eq('id', id);
  } catch (err) {
    console.error('Supabase delete error (note):', err);
  }
};

export const deleteFolderFromSupabase = async (id: string) => {
  if (!supabase) return;
  try {
    await supabase.from('folders').delete().eq('id', id);
  } catch (err) {
    console.error('Supabase delete error (folder):', err);
  }
};

export const fetchInitialDataFromSupabase = async (): Promise<{ notes: Note[], folders: Folder[] } | null> => {
  if (!supabase) return null;
  try {
    const [notesRes, foldersRes] = await Promise.all([
      supabase.from('notes').select('*'),
      supabase.from('folders').select('*')
    ]);

    if (notesRes.error || foldersRes.error) {
      console.error('Failed to fetch from Supabase:', notesRes.error || foldersRes.error);
      return null;
    }

    return {
      notes: notesRes.data as Note[],
      folders: foldersRes.data as Folder[]
    };
  } catch (err) {
    console.error('Error fetching initial data from Supabase:', err);
    return null;
  }
};
