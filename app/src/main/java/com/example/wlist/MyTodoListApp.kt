package com.example.wlist

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.example.wlist.data.TasksRepository
import com.example.wlist.data.TasksRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyTodoListApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }

    val tasksRepository: TasksRepository by lazy { TasksRepositoryImpl(database.tasksDao()) }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            AppDatabase.getDatabase(this@MyTodoListApp)
        }

        applySavedPreferences()

        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                        updateTaskWidgets(context)
                    }
                }
            },
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        )
    }

    private fun applySavedPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val themeValue = prefs.getString("dark_mode_key", "system") ?: "system"
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        val languageValue = prefs.getString("language_key", "system") ?: "system"
        val appLocale: LocaleListCompat = when (languageValue) {
            "pt" -> LocaleListCompat.forLanguageTags("pt-BR")
            "en" -> LocaleListCompat.forLanguageTags("en-US")
            "es" -> LocaleListCompat.forLanguageTags("es-ES")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}