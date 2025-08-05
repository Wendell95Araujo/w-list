package com.example.todolist.data

import com.example.todolist.dao.TasksDao
import com.example.todolist.model.TaskItem
import kotlinx.coroutines.runBlocking

interface TasksRepository {
    fun getUncompletedTasksForWidget(): List<TaskItem>
}

class TasksRepositoryImpl(private val tasksDao: TasksDao) : TasksRepository {
    override fun getUncompletedTasksForWidget(): List<TaskItem> {
        return runBlocking {
            tasksDao.getUncompletedTasksForWidget()
        }
    }
}