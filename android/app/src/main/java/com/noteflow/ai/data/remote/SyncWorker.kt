package com.noteflow.ai.data.remote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.repository.SyncRepository
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Background sync worker. Runs on the periodic schedule and on-demand;
 * WorkManager retries with exponential backoff when the network is
 * unavailable or the backend returns an error.
 *
 * Uses [EntryPoints] instead of @HiltWorker to avoid the extra hilt-work
 * dependency; the worker is constructed by WorkManager's default factory.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntryPoint {
        fun syncRepository(): SyncRepository
    }

    override suspend fun doWork(): androidx.work.Result {
        val syncRepository = EntryPoints.get(
            applicationContext,
            EntryPoint::class.java
        ).syncRepository()
        return when (val result = syncRepository.syncNow()) {
            is Result.Success -> androidx.work.Result.success()
            is Result.Failure -> {
                // Retry transient failures; give up on configuration errors.
                if (result.message?.contains("not configured") == true ||
                    result.message?.contains("Sign in") == true
                ) {
                    androidx.work.Result.failure()
                } else {
                    androidx.work.Result.retry()
                }
            }
        }
    }
}
