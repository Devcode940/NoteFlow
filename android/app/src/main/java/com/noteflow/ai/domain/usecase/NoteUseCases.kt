package com.noteflow.ai.domain.usecase

import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.repository.NoteRepository
import javax.inject.Inject

/**
 * Use cases encapsulate note workflows so ViewModels stay thin and the rules
 * are unit-testable with a fake repository.
 */

class CreateNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(title: String = "", content: String = "", folderId: String? = null): Result<Note> =
        repository.createNote(title, content, folderId)
}

class UpdateNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(note: Note): Result<Unit> = repository.updateNote(note)
}

class SaveNoteContentUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    /** Autosave path: persists title + content and recomputes counts. */
    suspend operator fun invoke(id: String, title: String, content: String): Result<Unit> =
        repository.updateContent(id, title, content)
}

class TrashNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.trashNote(id)
}

class RestoreNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.restoreNote(id)
}

class DeleteNotePermanentlyUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteNotePermanently(id)
}

class DuplicateNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String): Result<Note> = repository.duplicateNote(id)
}

class TogglePinUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String, pinned: Boolean): Result<Unit> =
        repository.setPinned(id, pinned)
}

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String, favorite: Boolean): Result<Unit> =
        repository.setFavorite(id, favorite)
}

class ToggleArchiveUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: String, archived: Boolean): Result<Unit> =
        repository.setArchived(id, archived)
}

class SearchNotesUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(query: String): Result<List<Note>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.Success(emptyList())
        return repository.searchNotes(trimmed)
    }
}
