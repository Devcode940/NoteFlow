-- NoteFlow Supabase schema (v2 — column names now match the Android DTOs 1:1)
-- Run in the Supabase SQL Editor. This file REPLACES supabase-schema.sql v1.
--
-- CRITICAL FIX (P0-2): v1 used camelCase quoted identifiers ("createdAt",
-- "owner", "folderId", "order") while the app's PostgREST DTOs send
-- snake_case (user_id, created_at, folder_id, sort_order). Every sync call
-- failed. If you created tables with v1, migrate with:
--   ALTER TABLE notes RENAME COLUMN "owner" TO user_id;
--   ALTER TABLE notes RENAME COLUMN "folderId" TO folder_id;
--   ALTER TABLE notes RENAME COLUMN "createdAt" TO created_at;
--   ALTER TABLE notes RENAME COLUMN "updatedAt" TO updated_at;
--   ALTER TABLE folders RENAME COLUMN "owner" TO user_id;
--   ALTER TABLE folders RENAME COLUMN "order" TO sort_order;
--   ALTER TABLE folders RENAME COLUMN "createdAt" TO created_at;
--   ALTER TABLE folders RENAME COLUMN "updatedAt" TO updated_at;

-- ---------------------------------------------------------------------------
-- folders
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS folders (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,              -- Firebase UID (== auth.uid())
    name TEXT NOT NULL,
    icon TEXT DEFAULT '📁',
    color TEXT DEFAULT '#7c3aed',
    sort_order BIGINT DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

-- ---------------------------------------------------------------------------
-- notes
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,              -- Firebase UID (== auth.uid())
    title TEXT,
    content TEXT,
    folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL,
    tags TEXT[] DEFAULT '{}',
    pinned BOOLEAN DEFAULT FALSE,
    favorite BOOLEAN DEFAULT FALSE,
    archived BOOLEAN DEFAULT FALSE,
    trashed BOOLEAN DEFAULT FALSE,
    created_at BIGINT,
    updated_at BIGINT
);

-- ---------------------------------------------------------------------------
-- Indexes (sync pulls are ordered by updated_at DESC with limit/offset)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_notes_user_updated ON notes(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_user_folder ON notes(user_id, folder_id);
CREATE INDEX IF NOT EXISTS idx_folders_user ON folders(user_id);

-- ---------------------------------------------------------------------------
-- Row Level Security
--   auth.uid() returns the JWT `sub` claim — the Firebase UID — when the
--   project is configured to accept Firebase JWTs as a third-party auth
--   provider (Supabase Dashboard → Authentication → Third-Party Auth).
--   The Android app sends `Authorization: Bearer <Firebase ID token>` and
--   `apikey: <anon key>`; RLS below is the sole guardrail that restricts
--   every row to its owner.
-- ---------------------------------------------------------------------------
ALTER TABLE folders ENABLE ROW LEVEL SECURITY;
ALTER TABLE notes ENABLE ROW LEVEL SECURITY;

-- Owners can do everything on their own rows; nothing on others'.
CREATE POLICY folders_owner_all ON folders
    FOR ALL
    TO authenticated
    USING (auth.uid()::text = user_id)
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY notes_owner_all ON notes
    FOR ALL
    TO authenticated
    USING (auth.uid()::text = user_id)
    WITH CHECK (auth.uid()::text = user_id);

-- Deny all access to anonymous (unauthenticated) users, defense in depth:
-- with RLS enabled and no permissive policy, anon sees zero rows, but the
-- explicit policies document the intent.
DROP POLICY IF EXISTS folders_deny_anonymous ON folders;
DROP POLICY IF EXISTS notes_deny_anonymous ON notes;
CREATE POLICY folders_deny_anonymous ON folders
    FOR ALL
    TO anon
    USING (false)
    WITH CHECK (false);

CREATE POLICY notes_deny_anonymous ON notes
    FOR ALL
    TO anon
    USING (false)
    WITH CHECK (false);

-- ---------------------------------------------------------------------------
-- Sync strategy
--   The Android app owns client-side last-write-wins merge (domain/SyncMerge):
--   pushes are idempotent upserts of the full row (Prefer: merge-duplicates),
--   pulls apply only rows with strictly newer updated_at. Supabase needs no
--   conflict-resolution logic. wordCount/charCount/targetWordCount and
--   syncState/lastSyncedAt are intentionally LOCAL-ONLY columns — they are
--   device-derived metadata and are not exchanged (the DTOs exclude them).
-- ---------------------------------------------------------------------------
