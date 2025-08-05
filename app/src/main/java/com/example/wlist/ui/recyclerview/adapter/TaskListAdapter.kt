package com.example.wlist.ui.recyclerview.adapter

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.wlist.R
import com.example.wlist.model.TaskDateCategory
import com.example.wlist.model.TaskItem

class TaskListAdapter(
    private val context: Context,
    private var listItems: MutableList<TaskListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    var onItemClick: ((TaskItem) -> Unit)? = null
    var onCompletedChanged: ((TaskItem, Boolean) -> Unit)? = null

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = itemView.findViewById(R.id.tasks)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val datetime: TextView = itemView.findViewById(R.id.datetime)
        private val completedCheckbox: CheckBox = itemView.findViewById(R.id.task_completed_checkbox)
        private val urgencyIndicator: View = itemView.findViewById(R.id.urgency_indicator)

        init {
            itemView.setOnClickListener {
                getTask()?.let { onItemClick?.invoke(it) }
            }
        }

        fun bind(task: TaskItem) {
            name.text = task.name
            description.text = task.description
            datetime.text = task.datetime
            completedCheckbox.setOnCheckedChangeListener(null)
            completedCheckbox.isChecked = task.isCompleted
            updateTextStyle(task.isCompleted)
            urgencyIndicator.setBackgroundColor(
                if (task.getDateCategory() == TaskDateCategory.OVERDUE) ContextCompat.getColor(context, R.color.task_overdue_red)
                else ContextCompat.getColor(context, android.R.color.transparent)
            )

            completedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                getTask()?.let { onCompletedChanged?.invoke(it, isChecked) }
            }
        }

        private fun getTask(): TaskItem? {
            val position = bindingAdapterPosition
            return if (position != RecyclerView.NO_POSITION) {
                (listItems.getOrNull(position) as? TaskListItem.ContentItem)?.task
            } else {
                null
            }
        }

        private fun updateTextStyle(isCompleted: Boolean) {
            val paintFlags = if (isCompleted) Paint.STRIKE_THRU_TEXT_FLAG else 0
            name.paintFlags = paintFlags
            description.paintFlags = paintFlags
            datetime.paintFlags = paintFlags
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.date_header_title)
        fun bind(category: TaskDateCategory) {
            val textResId = when (category) {
                TaskDateCategory.OVERDUE -> R.string.header_overdue
                TaskDateCategory.TODAY -> R.string.header_today
                TaskDateCategory.TOMORROW -> R.string.header_tomorrow
                TaskDateCategory.THIS_WEEK -> R.string.header_this_week
                TaskDateCategory.THIS_MONTH -> R.string.header_this_month
                TaskDateCategory.NEXT_MONTHS -> R.string.header_next_months
                TaskDateCategory.COMPLETED -> R.string.header_completed
            }
            title.text = context.getString(textResId)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is TaskListItem.HeaderItem -> TYPE_HEADER
            is TaskListItem.ContentItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_task_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = listItems[position]) {
            is TaskListItem.HeaderItem -> (holder as HeaderViewHolder).bind(currentItem.category)
            is TaskListItem.ContentItem -> (holder as TaskViewHolder).bind(currentItem.task)
        }
    }

    override fun getItemCount(): Int = listItems.size

    fun update(newList: List<TaskListItem>) {
        val diffCallback = TaskDiffCallback(this.listItems, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.listItems.clear()
        this.listItems.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getTaskAt(position: Int): TaskItem? {
        return (listItems.getOrNull(position) as? TaskListItem.ContentItem)?.task
    }

    fun getCurrentList(): List<TaskListItem> {
        return listItems
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < listItems.size && toPosition < listItems.size) {
            val fromItem = listItems.removeAt(fromPosition)
            listItems.add(toPosition, fromItem)
            notifyItemMoved(fromPosition, toPosition)
        }
    }
}