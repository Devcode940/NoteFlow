import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Applied only when google-services.json is present so the app can still be
    // built and used fully offline (local notes) without Firebase configured.
    if (file("google-services.json").exists()) {
        alias(libs.plugins.google.services)
    }
}

fun gradleProp(key: String, default: String = ""): String {
    val fromProject = project.findProperty(key) as? String
    if (!fromProject.isNullOrBlank()) return fromProject
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties()
        localProps.inputStream().use(props::load)
        (props.getProperty(key) ?: default).let { return it }
    }
    return default
}

android {
    namespace = "com.noteflow.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noteflow.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Non-secret configuration only. API keys are never baked into the app:
        // AI calls go through the secure backend, which holds GEMINI_API_KEY.
        buildConfigField("String", "BACKEND_URL", "\"${gradleProp("noteflow.backendUrl", "http://10.0.2.2:3000/")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${gradleProp("noteflow.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${gradleProp("noteflow.supabaseAnonKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${gradleProp("noteflow.googleWebClientId")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing is configured via keystore.properties (see android/README.md).
            // Without it, assembleRelease produces an unsigned APK/AAB.
            val keystoreProps = rootProject.file("keystore.properties")
            if (keystoreProps.exists()) {
                val props = Properties()
                keystoreProps.inputStream().use(props::load)
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose + Material 3
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (offline-first persistence)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore (preferences)
    implementation(libs.datastore.preferences)

    // WorkManager (background sync)
    implementation(libs.workmanager.ktx)

    // Networking (AI backend + Supabase PostgREST)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading (profile photos)
    implementation(libs.coil.compose)

    // Firebase Authentication + Google Sign-In (Credential Manager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.credentials.google.id)

    // Storage Access Framework helpers
    implementation(libs.documentfile)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
