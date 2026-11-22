package com.noteflow.ai.core.common

import android.content.Context
import android.util.Log
import com.noteflow.ai.BuildConfig
import java.io.File
import java.time.Instant

/**
 * Minimal structured logging facade.
 *
 * Rules enforced by convention (see CONTRIBUTING section of the README):
 *  - never log note content, tokens, emails or any user data — callers pass
 *    technical context only;
 *  - debug logs are compiled out of release builds;
 *  - warnings and errors always carry a throwable when one exists so crash
 *    diagnostics have real stack traces.
 */
object Logger {

    private const val PREFIX = "NoteFlow."

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(sanitize(tag), message)
    }

    fun i(tag: String, message: String) {
        Log.i(sanitize(tag), message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(sanitize(tag), message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(sanitize(tag), message, throwable)
    }

    private fun sanitize(tag: String): String = (PREFIX + tag).take(23)
}

/**
 * Local crash diagnostics: writes uncaught exceptions to a bounded on-device
 * log so field failures can be inspected without a third-party reporter.
 * Stack traces only — no user content is ever written.
 */
object CrashGuard {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_BYTES = 256 * 1024

    fun install(context: Context) {
        val filesDir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(filesDir, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun append(filesDir: File, thread: Thread, throwable: Throwable) {
        val file = File(filesDir, FILE_NAME)
        if (file.length() > MAX_BYTES) file.delete()
        file.appendText(
            "\n${Instant.now()} thread=${thread.name}\n" +
                throwable.stackTraceToString().take(8_000) + "\n"
        )
    }

    /** Returns and clears the crash log, for diagnostics screens or bug reports. */
    fun drain(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val content = runCatching { file.readText() }.getOrNull()
        file.delete()
        return content?.ifBlank { null }
    }
}
