# NoteFlow AI release rules.
# Keep kotlinx.serialization generated serializers and Room entities.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.noteflow.ai.** {
    *** Companion;
}

-keepclassmembers class com.noteflow.ai.data.local.** {
    ***;
}

# Retrofit service interfaces
-keepattributes Signature, Exceptions
-keep class com.noteflow.ai.data.remote.**Api { *; }
