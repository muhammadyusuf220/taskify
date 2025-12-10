package com.example.taskify.data.local

import android.content.Context
import androidx.room.*
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE user_id = :userId AND is_deleted = 0 ORDER BY created_at DESC")
    fun getAllNotes(userId: Int): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE note_id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE user_id = :userId")
    suspend fun deleteAllUserNotes(userId: Int)

    @Query("SELECT * FROM notes WHERE firestore_id = :firestoreId LIMIT 1")
    suspend fun getNoteByFirestoreId(firestoreId: String): Note?

    @Query("SELECT * FROM notes WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedNotes(userId: Int): List<Note>

    @Query("UPDATE notes SET is_synced = 1 WHERE note_id = :noteId")
    suspend fun markAsSynced(noteId: Int)

    @Query("SELECT * FROM notes WHERE user_id = :userId AND is_deleted = 1")
    suspend fun getDeletedNotes(userId: Int): List<Note>
}