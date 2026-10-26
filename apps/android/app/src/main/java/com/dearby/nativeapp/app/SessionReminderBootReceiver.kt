package com.dearby.nativeapp.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dearby.nativeapp.features.reminder.SessionReminders

/**
 * Alarms do not survive a reboot: at `BOOT_COMPLETED` the reminders this device had planned ([DevicePreferences.sessionReminders]) are set again
 * (future ones only), unless 세션 이동 알림 is off. The same when the user allows exact alarms in the system settings (Android 12+
 * `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`), so the pending ones move from inexact to exact. The app's own plan ([MainActivity]) takes over at the next start.
 */
class SessionReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val device = DevicePreferences(context)
        if (!device.sessionRemindersEnabled) return
        val now = System.currentTimeMillis()
        SessionReminders.schedule(context, device.sessionReminders.filter { it.at > now })
    }
}
