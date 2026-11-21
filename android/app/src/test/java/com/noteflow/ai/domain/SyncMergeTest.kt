package com.noteflow.ai.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.SyncState
import java.util.Date

class SyncMergeTest {

    @Test
    fun remoteWinsWhenStrictlyNewer() {
        val local = Note("1", "local", "local content", null, emptyList(),
            updatedAt = 1000)
        val remote = Note("1", "remote", "remote content", null, emptyList(),
            updatedAt = 2000)
        val merged = SyncMerge.merge(local, remote)
        assertEquals("remote content", merged.content)
        assertEquals("remote", merged.title)
    }

    @Test
    fun localWinsOnTie() {
        val local = Note("1", "local", "local content", null, emptyList(),
            updatedAt = 1000)
        val remote = Note("1", "remote", "remote content", null, emptyList(),
            updatedAt = 1000)
        val merged = SyncMerge.merge(local, remote)
        assertEquals("local content", merged.content)
        assertEquals("local", merged.title)
    }

    @Test
    fun localWinsWhenOlderRemote() {
        val local = Note("1", "local", "local content", null, emptyList(),
            updatedAt = 2000)
        val remote = Note("1", "remote", "remote content", null, emptyList(),
            updatedAt = 1000)
        val merged = SyncMerge.merge(local, remote)
        assertEquals("local content", merged.content)
    }

    @Test
    fun syncStateDefaults() {
        val note = Note("1", "t", "c", null, emptyList())
        assertEquals(SyncState.LOCAL_ONLY, note.syncState)
    }
}
