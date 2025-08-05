package com.example.todolist

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.example.todolist.ui.activity.TaskListActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.example.todolist.ACTION_SHOW_NOTIFICATION"
        const val ACTION_MARK_AS_DONE = "com.example.todolist.ACTION_MARK_AS_DONE"
        const val ACTION_SNOOZE = "com.example.todolist.ACTION_SNOOZE"

        const val NOTIFICATION_ID_KEY = "notification_id"
        const val TASK_ID_KEY = "task_id"
        const val TASK_NAME_KEY = "task_name"
        const val TASK_DATETIME_KEY = "task_datetime"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_NOTIFICATION -> showNotification(context, intent)
            ACTION_MARK_AS_DONE -> handleMarkAsDone(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val taskName = intent.getStringExtra(TASK_NAME_KEY) ?: context.getString(R.string.notification_default_text)
        val taskId = intent.getStringExtra(TASK_ID_KEY) ?: return
        val taskDatetime = intent.getStringExtra(TASK_DATETIME_KEY) ?: ""
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_KEY, 0)

        val formattedTime = extractTimeFromDateTimeString(taskDatetime)
        val notificationText = if (formattedTime.isNotBlank()) {
            context.getString(R.string.notification_text, taskName, formattedTime)
        } else {
            taskName
        }

        val doneIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MARK_AS_DONE
            putExtra(TASK_ID_KEY, taskId)
            putExtra(NOTIFICATION_ID_KEY, notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 1, doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(TASK_ID_KEY, taskId)
            putExtra(TASK_NAME_KEY, taskName)
            putExtra(TASK_DATETIME_KEY, taskDatetime)
            putExtra(NOTIFICATION_ID_KEY, notificationId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 2, snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activityIntent = Intent(context, TaskListActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            context, notificationId, activityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "task_reminders"
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_list_alt_check_24)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(activityPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check_24, context.getString(R.string.notification_action_done), donePendingIntent)
            .addAction(R.drawable.ic_snooze_24, context.getString(R.string.notification_action_snooze), snoozePendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun handleMarkAsDone(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TASK_ID_KEY) ?: return
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_KEY, 0)

        CoroutineScope(Dispatchers.IO).launch {
            val tasksDao = AppDatabase.getDatabase(context).tasksDao()
            val task = tasksDao.getTaskById(taskId)
            if (task != null) {
                tasksDao.updateTask(task.copy(isCompleted = true))
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_KEY, 0)

        val newTimeInMillis = System.currentTimeMillis() + 10 * 60 * 1000

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alarmIntent = intent.apply { action = ACTION_SHOW_NOTIFICATION }

        val pendingIntent = PendingIntent.getBroadcast(
            context, notificationId, alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            newTimeInMillis,
            pendingIntent
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun extractTimeFromDateTimeString(dateTimeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateTimeString)
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}