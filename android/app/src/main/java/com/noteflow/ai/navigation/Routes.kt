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

    fun notes(filter: NoteFilterType, folderId: String? = null, tag: String? = null): String {
        val base = "$NOTES/${filter.name}"
        return when (filter) {
            NoteFilterType.FOLDER -> "$base/${folderId.orEmpty()}"
            NoteFilterType.TAG -> "$base/${tag.orEmpty()}"
            else -> base
        }
    }

    const val NOTES_PATTERN = "notes/{filter}/{arg}"
}
