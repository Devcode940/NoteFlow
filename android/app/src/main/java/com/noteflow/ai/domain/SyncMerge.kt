package com.noteflow.ai.domain

/**
 * Conflict resolution for offline-first sync.
 *
 * Rule (last-write-wins with a local bias on ties): the version with the
 * newer [updatedAt] wins; on equal timestamps the local version wins so a
 * device never silently discards its own most recent edit. This guarantees
 * newer content is never overwritten by older synchronized content.
 */
object SyncMerge {

    enum class Winner { LOCAL, REMOTE }

    fun resolveWinner(localUpdatedAt: Long, remoteUpdatedAt: Long): Winner =
        if (remoteUpdatedAt > localUpdatedAt) Winner.REMOTE else Winner.LOCAL

    /** True when the remote version should replace the local one. */
    fun shouldApplyRemote(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
        resolveWinner(localUpdatedAt, remoteUpdatedAt) == Winner.REMOTE

    /** True when the local version must be pushed because it is newer. */
    fun shouldPushLocal(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
        resolveWinner(localUpdatedAt, remoteUpdatedAt) == Winner.LOCAL
}
