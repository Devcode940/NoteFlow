package com.noteflow.ai.di

import android.content.Context
import androidx.room.Room
import com.noteflow.ai.data.local.AppDatabase
import com.noteflow.ai.data.local.FolderDao
import com.noteflow.ai.data.local.NoteDao
import com.noteflow.ai.data.local.NoteTagDao
import com.noteflow.ai.data.local.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "noteflow.db")
            // No destructive migrations: schema changes require an explicit
            // Migration. Downgrades are rejected rather than wiping user data.
            .build()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideNoteTagDao(db: AppDatabase): NoteTagDao = db.noteTagDao()

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()
}
