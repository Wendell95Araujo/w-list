package com.example.todolist.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.todolist.model.TaskItem

@Dao
interface TasksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem)

    @Update
    suspend fun updateTask(task: TaskItem)

    @Update
    suspend fun updateTasks(tasks: List<TaskItem>)

    @Delete
    suspend fun deleteTask(task: TaskItem)

    @Query("SELECT * FROM tasks_table ORDER BY displayOrder ASC")
    suspend fun getAllTasks(): List<TaskItem>

    @Query("SELECT * FROM tasks_table WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskItem?

    @Query("SELECT * FROM tasks_table WHERE nameNormalized LIKE :query ORDER BY displayOrder ASC")
    suspend fun searchTasksByName(query: String): List<TaskItem>

    @Query("SELECT * FROM tasks_table WHERE isCompleted = 0 ORDER BY displayOrder ASC LIMIT 5")
    suspend fun getUncompletedTasksForWidget(): List<TaskItem>
}