-- NoteFlow Supabase Schema (hardened)
-- Run this in your Supabase SQL Editor.
-- Requires: a Supabase project with the `pgjwt` extension enabled (default).
-- Auth: users authenticate via Firebase; the Firebase UID is stored as the
--       `owner` column and RLS guarantees users only access their own rows.

-- ---------------------------------------------------------------------------
-- folders
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS folders (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL,                 -- Firebase UID
    name TEXT NOT NULL,
    icon TEXT DEFAULT '📁',
    color TEXT DEFAULT '#7c3aed',
    "order" INTEGER DEFAULT 0,
    "createdAt" BIGINT,
    "updatedAt" BIGINT
);

-- ---------------------------------------------------------------------------
-- notes
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL,                 -- Firebase UID
    title TEXT,
    content TEXT,
    "folderId" TEXT REFERENCES folders(id) ON DELETE SET NULL,
    "createdAt" BIGINT,
    "updatedAt" BIGINT,
    trashed BOOLEAN DEFAULT FALSE,
    pinned BOOLEAN DEFAULT FALSE,
    favorite BOOLEAN DEFAULT FALSE,
    archived BOOLEAN DEFAULT FALSE,
    tags TEXT[] DEFAULT '{}',
    "wordCount" INTEGER DEFAULT 0,
    "charCount" INTEGER DEFAULT 0,
    "targetWordCount" INTEGER,
    syncState TEXT DEFAULT 'LOCAL_ONLY',
    "lastSyncedAt" BIGINT
);

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_notes_owner_updated ON notes(owner, "updatedAt" DESC);
CREATE INDEX IF NOT EXISTS idx_notes_owner_folder ON notes(owner, "folderId");
CREATE INDEX IF NOT EXISTS idx_folders_owner ON folders(owner);

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------
ALTER TABLE folders ENABLE ROW LEVEL SECURITY;
ALTER TABLE notes ENABLE ROW LEVEL SECURITY;

-- Folders: owners can do everything on their own folders; nothing on others'.
CREATE POLICY folders_owner_all ON folders
    FOR ALL
    TO authenticated
    USING (auth.uid()::text = owner)
    WITH CHECK (auth.uid()::text = owner);

-- Notes: owners can do everything on their own notes; nothing on others'.
CREATE POLICY notes_owner_all ON notes
    FOR ALL
    TO authenticated
    USING (auth.uid()::text = owner)
    WITH CHECK (auth.uid()::text = owner);

-- Deny all access to anonymous (unauthenticated) users.
CREATE POLICY folders_deny_anonymous ON folders
    FOR ALL
    TO anon
    USING (false);

CREATE POLICY notes_deny_anonymous ON notes
    FOR ALL
    TO anon
    USING (false);

-- ---------------------------------------------------------------------------
-- Notes on sync strategy
-- ---------------------------------------------------------------------------
-- The Android app owns client-side last-write-wins merge (SyncMerge). It only
-- writes rows where the remote `updatedAt` is strictly newer than local, so
-- Supabase never needs conflict resolution. RLS above is the sole guardrail:
-- a user can never read or write another user's notes/folders.

-- ---------------------------------------------------------------------------
-- To apply: in Supabase Dashboard → SQL Editor, paste and run this file.
-- ---------------------------------------------------------------------------
