package com.example.taskify.data.local

import android.content.Context
import androidx.room.*
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User

// Note DAO
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getAllNotes(userId: Int): List<Note>

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

    @Query("SELECT * FROM notes WHERE user_id = :userId AND (firestore_id IS NULL OR firestore_id = '')")
    suspend fun getUnsyncedNotes(userId: Int): List<Note>
}