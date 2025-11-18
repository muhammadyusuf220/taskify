package com.mobcom.taskify.data.local

import android.content.Context
import androidx.room.*
import com.mobcom.taskify.data.model.Note
import com.mobcom.taskify.data.model.Task

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getAllNotes(userId: String): List<Note>

    @Query("SELECT * FROM notes WHERE note_id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE user_id = :userId")
    suspend fun deleteAllUserNotes(userId: String)
}