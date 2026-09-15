package io.fixabley.dearby.app

import android.content.SharedPreferences

internal interface CalendarSettingsStore {
    val enabled: Boolean
    val firstPromptHandled: Boolean
    fun write(enabled: Boolean, firstPromptHandled: Boolean)
}

/** Separate nonpersonal boolean preferences; never stores calendar intervals or identifiers. */
internal class SharedPreferencesCalendarSettingsStore(private val preferences: SharedPreferences) : CalendarSettingsStore {
    override val enabled get() = preferences.getBoolean("enabled", false)
    override val firstPromptHandled get() = preferences.getBoolean("firstPromptHandled", false)
    override fun write(enabled: Boolean, firstPromptHandled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).putBoolean("firstPromptHandled", firstPromptHandled).apply()
    }
    companion object { const val FILE_NAME = "dearby.calendar.settings.v1" }
}
