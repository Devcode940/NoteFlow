# NoteFlow AI release rules (production-hardened).
# R8 keeps every rule below verified against the libraries in
# gradle/libs.versions.toml; run assembleRelease after any dependency bump
# and exercise sign-in / sync / AI / export in the release build.

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# ── kotlinx.serialization ─────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
# Keep the generated serializers for every @Serializable class in the app.
-keepclassmembers @kotlinx.serialization.Serializable class com.noteflow.ai.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.noteflow.ai.**$$serializer { *; }
-keepclassmembers class com.noteflow.ai.** {
    *** Companion;
}

# ── Room ──────────────────────────────────────────────────────────────
-keep class com.noteflow.ai.data.local.** { *; }

# ── Retrofit service interfaces ───────────────────────────────────────
-keep interface com.noteflow.ai.data.remote.*Api { *; }
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Retrofit + kotlinx-serialization converter use reflection on generic signatures.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ── OkHttp ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ── Firebase Auth ─────────────────────────────────────────────────────
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.**

# ── Credential Manager ────────────────────────────────────────────────
-keep class androidx.credentials.playservices.** { *; }

# ── WorkManager / Hilt workers ────────────────────────────────────────
-keep class com.noteflow.ai.data.remote.SyncWorker { *; }

# ── Coroutines debug metadata (small, keeps stack traces actionable) ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
