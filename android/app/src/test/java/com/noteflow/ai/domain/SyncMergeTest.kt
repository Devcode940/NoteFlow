package com.noteflow.ai.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.SyncState

/**
 * Regression tests for the last-write-wins conflict rule (SyncMerge).
 * Note construction uses the full required parameters (createdAt/updatedAt
 * have no defaults) — the previous version of this file did not compile.
 */
class SyncMergeTest {

    private fun note(updatedAt: Long, title: String = "local"): Note = Note(
        id = "1",
        title = title,
        content = "$title content",
        folderId = null,
        tags = emptyList(),
        createdAt = 0,
        updatedAt = updatedAt
    )

    @Test
    fun remoteWinsWhenStrictlyNewer() {
        assertTrue(SyncMerge.shouldApplyRemote(localUpdatedAt = 1000, remoteUpdatedAt = 2000))
        assertFalse(SyncMerge.shouldPushLocal(localUpdatedAt = 1000, remoteUpdatedAt = 2000))
    }

    @Test
    fun localWinsOnTie() {
        assertFalse(SyncMerge.shouldApplyRemote(localUpdatedAt = 1000, remoteUpdatedAt = 1000))
        assertTrue(SyncMerge.shouldPushLocal(localUpdatedAt = 1000, remoteUpdatedAt = 1000))
    }

    @Test
    fun localWinsWhenOlderRemote() {
        assertFalse(SyncMerge.shouldApplyRemote(localUpdatedAt = 2000, remoteUpdatedAt = 1000))
        assertTrue(SyncMerge.shouldPushLocal(localUpdatedAt = 2000, remoteUpdatedAt = 1000))
    }

    @Test
    fun syncStateDefaultsToLocalOnly() {
        val n = note(updatedAt = 100)
        assertEquals(SyncState.LOCAL_ONLY, n.syncState)
    }
}
