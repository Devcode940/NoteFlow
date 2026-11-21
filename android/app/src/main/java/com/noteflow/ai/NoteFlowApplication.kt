package com.noteflow.ai

import android.app.Application
import com.noteflow.ai.domain.repository.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NoteFlowApplication : Application() {

    @Inject
    lateinit var syncRepository: SyncRepository

    override fun onCreate() {
        super.onCreate()
        // Periodic background sync (WorkManager enforces the 15-minute minimum
        // interval and connectivity constraints internally).
        syncRepository.scheduleBackgroundSync()
    }
}
