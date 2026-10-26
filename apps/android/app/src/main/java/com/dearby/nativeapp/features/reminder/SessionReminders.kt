package com.dearby.nativeapp.features.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dearby.nativeapp.R
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import org.json.JSONObject

/**
 * One local 세션 이동 알림 (contract "찜한 세션 이동 알림", 2026-10-26): fired at [at] (epoch millis, 10 minutes before the session starts) with [title]
 * `10분 뒤 · {트랙} · {room}` and [body] the session title (a group of 찜 starting at the same instant adds `같은 시간에 내 시간표 세션 n개` on a second line
 * and follows the session with the smallest id, [sessionId]). A tap opens [activityId]'s timetable sheet for [scheduleId] on 내 시간표. The contract's
 * identifier `session-<sessionId>` hashed is [code], the alarm's request code and the notification id, so the same reminder set again replaces itself.
 */
data class Reminder(val at: Long, val sessionId: String, val title: String, val body: String, val activityId: String, val scheduleId: String) {
    val code get() = "session-$sessionId".hashCode()
    /** The device's copy of what was scheduled (app `DevicePreferences`), read back after a reboot. */
    fun toJson(): String = JSONObject().put("at", at).put("sessionId", sessionId).put("title", title).put("body", body).put("activityId", activityId).put("scheduleId", scheduleId).toString()
    companion object {
        fun parse(json: String): Reminder? = runCatching {
            val o = JSONObject(json)
            Reminder(o.getLong("at"), o.getString("sessionId"), o.getString("title"), o.getString("body"), o.getString("activityId"), o.getString("scheduleId"))
        }.getOrNull()
    }
}

/**
 * 찜한 세션 이동 알림 (contract "찜한 세션 이동 알림"), on the device only (no server part): `AlarmManager` alarms that [SessionReminderReceiver] turns into
 * notifications on the `세션 이동 알림` channel. Exact alarms (`setExactAndAllowWhileIdle`) need `SCHEDULE_EXACT_ALARM`, which Android 14+ denies until the user allows it
 * in the system settings ([exact]); without it the alarm is `setAndAllowWhileIdle`, which the system may deliver a few minutes late. The app keeps
 * its own copy of the planned reminders so it can cancel the ones no longer wanted and put them back after a reboot.
 */
object SessionReminders {
    const val CHANNEL = "session_reminder"
    /** Extras of the launch intent a notification tap sends (the contract's payload keys): [KIND] is [SESSION_REMINDER]; the activity and its block open on 찜한 세션. */
    const val KIND = "kind"
    const val SESSION_REMINDER = "session_reminder"
    const val ACTIVITY = "activityId"
    const val SCHEDULE = "scheduleId"
    private const val SESSION = "sessionId"
    private const val ACTION = "com.dearby.nativeapp.SESSION_REMINDER"
    private const val LEAD_MS = 10 * 60_000L
    /** The contract's cap (iOS's 64 pending local notifications): the nearest 60 groups. */
    private const val LIMIT = 60

    /** The `세션 이동 알림` channel; idempotent, created at every start. */
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(CHANNEL, "세션 이동 알림", NotificationManager.IMPORTANCE_HIGH))
    }

    /** Whether alarms fire at the exact minute (Android 12+: the user's `알람 및 리마인더` allowance; always on earlier versions). */
    fun exact(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /**
     * The reminders for [favorites] (내 시간표) whose time is still ahead of [now], nearest [LIMIT] first: one per start instant, 10 minutes before.
     * 찜 starting together are one reminder that follows the session with the smallest id (title, activity, identifier) and says how many there are.
     */
    fun plan(favorites: List<SessionFavorite>, now: Long): List<Reminder> = favorites
        .groupBy { it.session.startAt.toInstant().toEpochMilli() }
        .mapNotNull { (start, group) ->
            val at = start - LEAD_MS
            if (at <= now) return@mapNotNull null
            // Numeric ids compare by length first, so `9` < `10`; anything else falls back to text order.
            val lead = group.minWith(compareBy({ it.session.id.length }, { it.session.id }))
            val place = lead.track?.let { listOfNotNull(it.name, it.room).joinToString(" · ") }?.takeIf { it.isNotBlank() }
            val body = listOfNotNull(lead.session.title, "같은 시간에 내 시간표 세션 ${group.size}개".takeIf { group.size > 1 }).joinToString("\n")
            Reminder(at, lead.session.id, listOfNotNull("10분 뒤", place).joinToString(" · "), body, lead.activityId, lead.scheduleId)
        }
        .sortedBy { it.at }
        .take(LIMIT)

    /** Sets every alarm in [reminders] (one already set with the same [Reminder.code] is replaced) and cancels those in [cancel]. */
    fun schedule(context: Context, reminders: List<Reminder>, cancel: List<Reminder> = emptyList()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        for (gone in cancel) PendingIntent.getBroadcast(context, gone.code, alarmIntent(context, gone), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        val exact = exact(context)
        for (reminder in reminders) {
            val fire = PendingIntent.getBroadcast(context, reminder.code, alarmIntent(context, reminder), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, fire) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, fire)
        }
    }

    private fun alarmIntent(context: Context, reminder: Reminder) = Intent(context, SessionReminderReceiver::class.java).setAction(ACTION)
        .putExtra(SESSION, reminder.sessionId).putExtra("title", reminder.title).putExtra("body", reminder.body).putExtra(ACTIVITY, reminder.activityId).putExtra(SCHEDULE, reminder.scheduleId)

    /** Posts the notification an alarm [intent] carries; a tap starts the app with the payload extras (MainActivity opens the timetable). */
    internal fun notify(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val activityId = intent.getStringExtra(ACTIVITY) ?: return
        val scheduleId = intent.getStringExtra(SCHEDULE) ?: return
        val sessionId = intent.getStringExtra(SESSION) ?: return
        val code = "session-$sessionId".hashCode()
        val body = intent.getStringExtra("body")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .putExtra(KIND, SESSION_REMINDER).putExtra(ACTIVITY, activityId).putExtra(SCHEDULE, scheduleId).putExtra(SESSION, sessionId)
        val tap = PendingIntent.getActivity(context, code, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // BigTextStyle so a group's second line (`같은 시간에 내 시간표 세션 n개`) shows when expanded.
        val shown = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.dearby_symbol).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(tap).build()
        NotificationManagerCompat.from(context).notify(code, shown)
    }
}

/** The alarm's end: one notification per reminder. Declared in the manifest, not exported; only this app's alarms reach it. */
class SessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SessionReminders.notify(context, intent)
}
