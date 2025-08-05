package com.example.todolist.ui.recyclerview.adapter

import androidx.recyclerview.widget.DiffUtil
import com.example.todolist.model.TaskItem

sealed class TaskListItem {
    data class HeaderItem(val category: com.example.todolist.model.TaskDateCategory) : TaskListItem()
    data class ContentItem(val task: TaskItem) : TaskListItem()
}

class TaskDiffCallback(
    private val oldList: List<TaskListItem>,
    private val newList: List<TaskListItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        if (oldItem is TaskListItem.HeaderItem && newItem is TaskListItem.HeaderItem) {
            return oldItem.category == newItem.category
        }
        if (oldItem is TaskListItem.ContentItem && newItem is TaskListItem.ContentItem) {
            return oldItem.task.id == newItem.task.id
        }
        return false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}