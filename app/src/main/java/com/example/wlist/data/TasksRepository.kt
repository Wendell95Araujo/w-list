package com.example.wlist.data

import com.example.wlist.dao.TasksDao
import com.example.wlist.model.TaskItem
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