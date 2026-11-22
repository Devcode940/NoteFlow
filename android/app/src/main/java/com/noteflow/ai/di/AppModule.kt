package com.noteflow.ai.di

import com.noteflow.ai.BuildConfig
import com.noteflow.ai.data.remote.AiApi
import com.noteflow.ai.data.remote.SupabaseApi
import com.noteflow.ai.domain.repository.AiRepository
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.FolderRepository
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import com.noteflow.ai.domain.repository.SyncRepository
import com.noteflow.ai.data.local.PreferencesRepositoryImpl
import com.noteflow.ai.data.remote.AiRepositoryImpl
import com.noteflow.ai.data.remote.AuthRepositoryImpl
import com.noteflow.ai.data.remote.SyncManager
import com.noteflow.ai.data.repository.FolderRepositoryImpl
import com.noteflow.ai.data.repository.NoteRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(backendUrlInterceptor: BackendUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // AI generation can be slow
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(backendUrlInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideAiApi(okHttpClient: OkHttpClient, json: Json): AiApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AiApi::class.java)

    @Provides
    @Singleton
    fun provideSupabaseApi(okHttpClient: OkHttpClient, json: Json): SupabaseApi =
        Retrofit.Builder()
            // Supabase is optional in local builds; syncNow refuses to run
            // before this placeholder can ever be hit.
            .baseUrl(BuildConfig.SUPABASE_URL.ifBlank { "https://supabase.invalid/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)

    @Provides
    @Named("io")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Named("main")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncManager): SyncRepository
}
