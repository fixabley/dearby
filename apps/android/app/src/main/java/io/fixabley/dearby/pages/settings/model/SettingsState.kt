package io.fixabley.dearby.pages.settings.model

internal data class SettingsState(val enabled: Boolean, val waiting: Boolean, val message: String, val showSystemSettings: Boolean)
