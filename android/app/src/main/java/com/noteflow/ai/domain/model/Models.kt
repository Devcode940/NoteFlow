package com.noteflow.ai.domain.model

/** Sorting options for the note list. */
enum class SortBy { UPDATED_AT, CREATED_AT, TITLE, CUSTOM }

enum class SortOrder { ASC, DESC }

/** User-selectable theme. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Editor display modes. */
enum class EditorMode { EDIT, PREVIEW, FOCUS }

/** Per-note synchronization state shown in the UI. */
enum class SyncState { SYNCED, PENDING, SYNCING, FAILED, LOCAL_ONLY }

/** Supported export formats. */
enum class ExportFormat { TXT, MARKDOWN, HTML, PDF }

/**
 * A note. This is the single source of truth in the domain layer; the Room
 * entity mirrors it 1:1 so repositories never leak persistence types upward.
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val tags: List<String>,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long = 0L,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val targetWordCount: Int? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val lastSyncedAt: Long? = null
)

/** A user-defined folder. [icon] is an emoji glyph key, [colorHex] an ARGB hex string. */
data class Folder(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val sortOrder: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
)

/** Note list filters (sidebar destinations). */
enum class NoteFilterType { ALL, RECENT, FAVORITES, PINNED, ARCHIVED, TRASH, FOLDER, TAG }

data class NoteFilter(
    val type: NoteFilterType,
    val folderId: String? = null,
    val tag: String? = null
)

/** The 12 AI quick actions. [key] matches the backend /api/ai/action contract. */
enum class AiQuickAction(val key: String, val label: String, val description: String) {
    AUTOTITLE("autotitle", "Auto-title", "Generate a meaningful title"),
    SUMMARIZE("summarize", "Summarize", "Extract the key points"),
    IMPROVE("improve", "Improve", "Improve grammar and clarity"),
    EXPAND("expand", "Expand", "Expand the content"),
    SIMPLIFY("simplify", "Simplify", "Simplify complex text"),
    TRANSLATE("translate", "Translate", "Translate into another language"),
    FIX("fix", "Fix", "Correct grammar and spelling"),
    TONE("tone", "Tone", "Change the writing tone"),
    BULLETS("bullets", "Bullets", "Convert to Markdown lists"),
    HEADLINE("headline", "Headline", "Generate headlines"),
    TODOS("todos", "Todos", "Extract actionable tasks"),
    QUESTIONS("questions", "Questions", "Generate discussion questions");

    companion object {
        fun fromKey(key: String): AiQuickAction? = entries.firstOrNull { it.key == key }
    }
}

/** The 4 AI content generators. [key] matches the backend /api/ai/generate contract. */
enum class AiGeneratorType(val key: String, val label: String, val description: String) {
    OUTLINE("outline", "Outline", "Structured hierarchical outline"),
    DRAFT("draft", "Full draft", "Complete first draft"),
    IDEAS("ideas", "Ideas", "Brainstorm ideas and angles"),
    CONTINUE("continue", "Continue writing", "Continue from existing content");

    companion object {
        fun fromKey(key: String): AiGeneratorType? = entries.firstOrNull { it.key == key }
    }
}

enum class AiRole { USER, ASSISTANT }

data class AiMessage(
    val id: String,
    val role: AiRole,
    val content: String,
    val timestamp: Long
)

/** Authenticated user profile (Firebase). */
data class UserProfile(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

/** Persisted user preferences (DataStore). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultEditorMode: EditorMode = EditorMode.EDIT,
    val autoSave: Boolean = true,
    val autoSaveDelayMs: Long = 800L,
    val wordGoal: Int = 500,
    val aiModel: String = "gemini-2.5-flash",
    val backendUrl: String = "",
    val syncEnabled: Boolean = true,
    val defaultExportFormat: ExportFormat = ExportFormat.MARKDOWN,
    val aiRequestCount: Long = 0L,
    val aiLastUsedAt: Long? = null
)
