-- NoteFlow Supabase Schema
-- Run this in your Supabase SQL Editor

-- Create folders table
CREATE TABLE IF NOT EXISTS folders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT,
    "order" INTEGER DEFAULT 0,
    "createdAt" BIGINT
);

-- Create notes table
CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    title TEXT,
    content TEXT,
    "folderId" TEXT REFERENCES folders(id) ON DELETE SET NULL,
    "createdAt" BIGINT,
    "updatedAt" BIGINT,
    trashed BOOLEAN DEFAULT FALSE,
    tags TEXT[],
    "wordCount" INTEGER DEFAULT 0,
    "charCount" INTEGER DEFAULT 0,
    "targetWordCount" INTEGER
);

-- Note: In a production app, enable Row Level Security (RLS)
-- ALTER TABLE folders ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE notes ENABLE ROW LEVEL SECURITY;
