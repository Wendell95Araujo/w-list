package com.example.wlist

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.wlist.data.TasksRepository
import com.example.wlist.model.TaskItem
import android.content.res.Configuration

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val repository = (application as MyTodoListApp).tasksRepository
        return TaskWidgetItemFactory(this.applicationContext, repository)
    }
}

class TaskWidgetItemFactory(
    private val context: Context,
    private val tasksRepository: TasksRepository
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        tasks = tasksRepository.getUncompletedTasksForWidget()
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)

        val isDark = isDarkTheme(context)

        val taskNameColor = if (isDark) context.getColor(R.color.blue_200) else context.getColor(R.color.blue_900)
        val dateTimeColor = if (isDark) context.getColor(R.color.blue_100) else context.getColor(R.color.blue_700)

        views.setTextViewText(R.id.widget_task_name, task.name)
        views.setTextColor(R.id.widget_task_name, taskNameColor)

        val shortDateTime = task.datetime.split(" ").getOrNull(0) ?: ""
        views.setTextViewText(R.id.widget_task_datetime, shortDateTime)
        views.setTextColor(R.id.widget_task_datetime, dateTimeColor)

        val backgroundDrawable = if (isDark) {
            R.drawable.widget_task_item_background_dark
        } else {
            R.drawable.widget_task_item_background
        }
        views.setInt(R.id.widget_task_item_layout, "setBackgroundResource", backgroundDrawable)

        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_task_item_layout, fillInIntent)

        return views
    }

    private fun isDarkTheme(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks[position].id.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
}
