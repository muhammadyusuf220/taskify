// File: com/example/taskify/data/local/TaskDao.kt
package com.example.taskify.data.local

import androidx.room.*
import com.example.taskify.data.model.Task
import kotlinx.coroutines.flow.Flow // <--- Wajib Import

@Dao
interface TaskDao {
    // 1. Ubah jadi FLOW & Filter is_deleted (Hapus 'suspend')
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_deleted = 0 ORDER BY due_date ASC")
    fun getAllTasks(userId: Int): Flow<List<Task>>

    // 2. Ubah jadi FLOW & Filter is_deleted (Hapus 'suspend')
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_completed = 0 AND is_deleted = 0 ORDER BY due_date ASC")
    fun getIncompleteTasks(userId: Int): Flow<List<Task>>

    // Query Sync: Ambil yang belum naik ke server
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedTasks(userId: Int): List<Task>

    // Query Sync: Ambil sampah
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_deleted = 1")
    suspend fun getDeletedTasks(userId: Int): List<Task>

    // Helper: Tandai sudah sync
    @Query("UPDATE tasks SET is_synced = 1 WHERE task_id = :taskId")
    suspend fun markAsSynced(taskId: Int)

    @Query("SELECT * FROM tasks WHERE firestore_id = :firestoreId LIMIT 1")
    suspend fun getTaskByFirestoreId(firestoreId: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Update khusus checklist (tandai unsynced agar di-upload)
    @Query("UPDATE tasks SET is_completed = :isCompleted, is_synced = 0 WHERE task_id = :taskId")
    suspend fun updateTaskCompletion(taskId: Int, isCompleted: Boolean)
}