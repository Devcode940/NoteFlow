package com.noteflow.ai

import android.app.Application
import com.noteflow.ai.core.common.CrashGuard
import com.noteflow.ai.domain.repository.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NoteFlowApplication : Application() {

    @Inject
    lateinit var syncRepository: SyncRepository

    override fun onCreate() {
        super.onCreate()
        // Local crash diagnostics: uncaught exceptions are appended to a
        // bounded on-device log (crash_log.txt) before the system handler
        // runs, so field failures can be inspected without a third-party
        // reporter. Add Firebase Crashlytics here when configured.
        CrashGuard.install(this)
        // Periodic background sync (WorkManager enforces the 15-minute minimum
        // interval and connectivity constraints internally).
        syncRepository.scheduleBackgroundSync()
    }
}
