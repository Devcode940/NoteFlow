package com.noteflow.ai.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Supabase PostgREST DTOs. Tables (see /supabase-schema.sql):
 *  - notes(id, user_id, title, content, folder_id, tags[], pinned, favorite,
 *          archived, trashed, created_at, updated_at)
 *  - folders(id, user_id, name, icon, color, sort_order, created_at, updated_at)
 *
 * Row Level Security restricts every row to its owner (auth.uid()), so the
 * app can only ever read/write the signed-in user's data.
 */
@Serializable
data class SupabaseNoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    @SerialName("folder_id") val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class SupabaseFolderDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String = "📁",
    val color: String = "#7c3aed",
    @SerialName("sort_order") val sortOrder: Long = 0L,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

/**
 * PostgREST access. The `apikey` header carries the Supabase anon key and
 * `Authorization: Bearer <Firebase ID token>` identifies the user; Supabase
 * validates the JWT against the configured third-party auth provider and RLS
 * enforces ownership. See android/README.md for the Supabase auth setup.
 */
interface SupabaseApi {

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/notes")
    suspend fun upsertNote(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body note: SupabaseNoteDto
    )

    @GET("rest/v1/notes")
    suspend fun fetchNotes(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc"
    ): List<SupabaseNoteDto>

    @DELETE("rest/v1/notes")
    suspend fun deleteNote(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String
    )

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/folders")
    suspend fun upsertFolder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body folder: SupabaseFolderDto
    )

    @GET("rest/v1/folders")
    suspend fun fetchFolders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "sort_order.asc"
    ): List<SupabaseFolderDto>

    @DELETE("rest/v1/folders")
    suspend fun deleteFolder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String
    )
}
