package com.example.todolist.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.todolist.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val languagePreference: ListPreference? = findPreference("language_key")
        languagePreference?.setOnPreferenceChangeListener { _, newValue ->
            val language = newValue as String
            val appLocale: LocaleListCompat = when (language) {
                "pt" -> LocaleListCompat.forLanguageTags("pt-BR")
                "en" -> LocaleListCompat.forLanguageTags("en-US")
                "es" -> LocaleListCompat.forLanguageTags("es-ES")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(appLocale)
            true
        }

        val darkModePreference: ListPreference? = findPreference("dark_mode_key")
        darkModePreference?.setOnPreferenceChangeListener { _, newValue ->
            val mode = when (newValue as String) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            true
        }
    }
}