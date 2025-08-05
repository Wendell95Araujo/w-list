package com.example.wlist

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.example.wlist.ui.activity.HomeActivity

@Suppress("DEPRECATION")
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, javaClass.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        }
    }

    private fun isDarkTheme(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @Suppress("DEPRECATION")
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val isDark = isDarkTheme(context)
        val views = RemoteViews(context.packageName, R.layout.task_widget_layout)

        val backgroundDrawable = if (isDark) R.drawable.widget_background_dark else R.drawable.widget_background
        views.setInt(R.id.widget_layout, "setBackgroundResource", backgroundDrawable)

        val headerDrawable = if (isDark) R.drawable.bg_widget_header_dark else R.drawable.bg_widget_header
        views.setInt(R.id.widget_header, "setBackgroundResource", headerDrawable)

        val headerTextColor = if (isDark) context.getColor(R.color.black) else context.getColor(R.color.white)
        views.setTextColor(R.id.widget_header, headerTextColor)

        val emptyTextColor = if (isDark) context.getColor(R.color.white) else context.getColor(R.color.black)
        views.setTextColor(R.id.widget_empty_view, emptyTextColor)

        val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = toUri(Intent.URI_INTENT_SCHEME).toUri()
        }
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

        val launchAppIntent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, launchAppIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_header, pendingIntent)
        views.setPendingIntentTemplate(R.id.widget_list_view, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_empty_view, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

fun updateTaskWidgets(context: Context) {
    val intent = Intent(context, TaskWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    }
    val ids = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    context.sendBroadcast(intent)
}
