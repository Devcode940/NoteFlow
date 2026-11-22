package com.noteflow.ai.navigation

import com.noteflow.ai.domain.model.NoteFilterType

/** Navigation routes for NoteFlow. */
object Routes {
    const val NOTES = "notes"
    const val EDITOR = "editor/{noteId}"
    const val FOLDERS = "folders"
    const val TAGS = "tags"
    const val SEARCH = "search"
    const val AI = "ai"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val SIGN_IN = "signin"
    const val EXPORTS = "exports"

    /** Special editor id meaning "create a new note, then open it". */
    const val NEW_NOTE_ID = "new"

    fun editor(noteId: String): String = "editor/$noteId"

    /**
     * Builds a note-list route. {arg} is an OPTIONAL query argument so the
     * plain filters ("notes/ALL", "notes/FAVORITES", ...) match the same
     * composable as folder/tag routes (P0-1 fix — the previous pattern
     * required three segments and crashed the NavHost on cold start).
     */
    fun notes(filter: NoteFilterType, folderId: String? = null, tag: String? = null): String =
        when (filter) {
            NoteFilterType.FOLDER -> "$NOTES/${filter.name}?arg=${folderId.orEmpty()}"
            NoteFilterType.TAG -> "$NOTES/${filter.name}?arg=${tag.orEmpty()}"
            else -> "$NOTES/${filter.name}"
        }

    const val NOTES_PATTERN = "notes/{filter}?arg={arg}"
}
