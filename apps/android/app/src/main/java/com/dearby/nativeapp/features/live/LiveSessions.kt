package com.dearby.nativeapp.features.live

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** One 찜 session of the day's [LivePlan]: [start]/[end] epoch millis, [place] `트랙 B · 그랜드볼룸 B` (null for a session that spans every track). */
data class LiveEntry(val sessionId: String, val start: Long, val end: Long, val place: String?, val title: String, val scheduleId: String)

/** The notification's content at one instant inside the window; see [LiveSessions.frame]. */
data class LiveFrame(val current: LiveEntry?, val next: LiveEntry?, val lines: List<String>, val chip: String, val progress: Int?)

/**
 * The day's 행사 진행 중 plan (2026-10-26 user decision "컨퍼런스 참여 시 다이나믹 아일랜드처럼 표시"): the today activity of 오늘 내 행사 and its 찜 sessions
 * of that Seoul day, start order. The notification shows from 30 minutes before the first ([from]) to the latest end ([until]; a long 찜 may
 * outlast one that starts after it). Serialised into the alarm that redraws it and into the device preferences for the reboot restore.
 */
data class LivePlan(val activityId: String, val activityName: String, val entries: List<LiveEntry>) {
    val from get() = entries.first().start - LiveSessions.LEAD_MS
    val until get() = entries.maxOf { it.end }
    fun toJson(): String = JSONObject().put("activityId", activityId).put("activityName", activityName).put("entries", JSONArray().apply {
        entries.forEach { put(JSONObject().put("sessionId", it.sessionId).put("start", it.start).put("end", it.end).put("place", it.place ?: JSONObject.NULL).put("title", it.title).put("scheduleId", it.scheduleId)) }
    }).toString()
    companion object {
        fun parse(json: String): LivePlan? = runCatching {
            val o = JSONObject(json)
            val entries = o.getJSONArray("entries").let { array -> List(array.length()) { i ->
                val e = array.getJSONObject(i)
                LiveEntry(e.getString("sessionId"), e.getLong("start"), e.getLong("end"), e.optString("place").takeIf { !e.isNull("place") }, e.getString("title"), e.getString("scheduleId"))
            } }
            LivePlan(o.getString("activityId"), o.getString("activityName"), entries).takeIf { entries.isNotEmpty() }
        }.getOrNull()
    }
}

/**
 * 행사 당일 진행 중 알림 (2026-10-26, device only, no server part): one ongoing notification on the `행사 진행 중` channel while the today activity's 찜
 * sessions run — title the activity, body `지금 · 트랙 B · 그랜드볼룸 B · 세션 제목` and `다음 14:50 · 트랙 C · 방 · 제목 · 20분 뒤`, the running session's
 * progress by its end. On Android 16+ it is a live update (`ProgressStyle`, promoted ongoing: status bar chip, One UI 8 Now Bar); below, an ordinary ongoing
 * notification with a progress bar. The channel is (re)created before every post, since an alarm or the boot restore may run before the Activity.
 * It is redrawn by one `AlarmManager` alarm at the next change — the window start, every session start·end, 10 minutes before a start, and at most 5 minutes
 * apart meanwhile so the progress moves — and at every [apply] from the app (foreground, 찜 change, switch). Exact alarms follow the `알람 및 리마인더`
 * allowance like 세션 이동 알림; without it the system may redraw a few minutes late. After the last session ends everything is removed.
 * A tap or `내 시간표 보기` opens the activity and that block's timetable on 내 시간표 with the 세션 이동 알림 payload (`kind=session_reminder`), which MainActivity already handles.
 */
object LiveSessions {
    const val CHANNEL = "live_session"
    internal const val LEAD_MS = 30 * 60_000L
    private const val NEAR_MS = 10 * 60_000L
    private const val REFRESH_MS = 5 * 60_000L
    private const val ACTION = "com.dearby.nativeapp.LIVE_SESSION"
    private const val PLAN = "plan"
    /** The notification id and the alarm's request code: one of each, so a new plan replaces the old. */
    private val ID = "live-session".hashCode()
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** The `행사 진행 중` channel, default importance without sound or vibration; idempotent, created at every start. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "행사 진행 중", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The plan for [activityId] (오늘 내 행사's today activity): its 찜 in [favorites] starting on [day] (Seoul), start order; null without one. */
    fun plan(activityId: String, activityName: String, favorites: List<SessionFavorite>, day: LocalDate): LivePlan? {
        val entries = favorites.filter { it.activityId == activityId && it.session.startAt.atZoneSameInstant(seoul).toLocalDate() == day }
            .map { LiveEntry(it.session.id, it.session.startAt.toInstant().toEpochMilli(), it.session.endAt.toInstant().toEpochMilli(),
                it.track?.let { track -> listOfNotNull(track.name, track.room).joinToString(" · ") }?.takeIf { place -> place.isNotBlank() }, it.session.title, it.scheduleId) }
            .filter { it.end > it.start }.sortedBy { it.start }
        return entries.takeIf { it.isNotEmpty() }?.let { LivePlan(activityId, activityName, it) }
    }

    /**
     * Draws [plan] for [now] and sets the alarm for the next change; null, or a plan whose sessions all ended, removes the notification and the alarm.
     * Returns true while the plan is live: showing, or waiting for its window with the alarm armed.
     */
    fun apply(context: Context, plan: LivePlan?, now: Long = System.currentTimeMillis()): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = NotificationManagerCompat.from(context)
        val intent = Intent(context, LiveSessionReceiver::class.java).setAction(ACTION)
        val next = plan?.let { nextAlarm(it, now) }
        if (plan == null || next == null) {
            notifications.cancel(ID)
            PendingIntent.getBroadcast(context, ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { alarms?.cancel(it); it.cancel() }
            return false
        }
        frame(plan, now)?.let { notify(context, plan, it) } ?: notifications.cancel(ID)
        val fire = PendingIntent.getBroadcast(context, ID, intent.putExtra(PLAN, plan.toJson()), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true
        if (exact) alarms?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, fire) else alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, fire)
        return true
    }

    /**
     * When the alarm should redraw after [now]: the window start before it; inside, the nearest start, end, 10 minutes before a start or 5 minutes on
     * (so the progress moves); null once every session ended.
     */
    internal fun nextAlarm(plan: LivePlan, now: Long): Long? = when {
        now >= plan.until -> null
        now < plan.from -> plan.from
        else -> (plan.entries.flatMap { listOf(it.start, it.end, it.start - NEAR_MS) }.filter { it > now } + (now + REFRESH_MS)).min()
    }

    /**
     * What the notification shows at [now]: the running session ([current]; of two 찜 starting together, the first in 내 시간표 order), the first one
     * starting later ([next]), the body [lines] `지금 · 트랙 B · 그랜드볼룸 B · 제목` and `다음 14:50 · 트랙 C · 방 · 제목 · 20분 뒤`, the status bar [chip]
     * (the running session's end `~15:30`, else the next start) and the running session's [progress] in percent. Null outside the window.
     */
    internal fun frame(plan: LivePlan, now: Long): LiveFrame? {
        if (now < plan.from || now >= plan.until) return null
        val current = plan.entries.firstOrNull { it.start <= now && now < it.end }
        val next = plan.entries.firstOrNull { it.start > now }
        val lines = listOfNotNull(
            current?.let { listOfNotNull("지금", it.place, it.title).joinToString(" · ") },
            next?.let { listOfNotNull("다음 ${time(it.start)}", it.place, it.title, "${left(it.start - now)} 뒤").joinToString(" · ") })
        val chip = if (current != null) "~${time(current.end)}" else time(next?.start ?: plan.until)
        val progress = current?.let { ((now - it.start) * 100 / (it.end - it.start)).toInt().coerceIn(0, 100) }
        return LiveFrame(current, next, lines, chip, progress)
    }

    private fun notify(context: Context, plan: LivePlan, frame: LiveFrame) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = frame.lines.joinToString("\n")
        // The 세션 이동 알림 payload (features/reminder `kind`·`activityId`·`scheduleId`·`sessionId`): MainActivity opens the activity and the block on 내 시간표.
        val target = frame.current ?: frame.next ?: plan.entries.last()
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .putExtra("kind", "session_reminder").putExtra("activityId", plan.activityId).putExtra("scheduleId", target.scheduleId).putExtra("sessionId", target.sessionId)
        val tap = PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        createChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.dearby_symbol).setContentTitle(plan.activityName).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_EVENT).setContentIntent(tap)
            .addAction(0, "내 시간표 보기", tap)
            .setShowWhen(false).setRequestPromotedOngoing(true).setShortCriticalText(frame.chip)
        val progress = frame.progress
        if (progress != null) {
            // Android 16+: the live update's own progress style (Now Bar); below, the standard progress bar under the two lines.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) builder.setStyle(NotificationCompat.ProgressStyle().setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100))).setProgress(progress))
            else builder.setProgress(100, progress, false).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        } else builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }


    private fun time(at: Long) = clock.format(Instant.ofEpochMilli(at).atZone(seoul))
    /** `20분`, `1시간`, `1시간 20분`; never under a minute. */
    private fun left(ms: Long): String {
        val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "${minutes}분" else listOfNotNull("${minutes / 60}시간", "${minutes % 60}분".takeIf { minutes % 60 > 0 }).joinToString(" ")
    }
}

/** The redraw alarm's end: draws the plan it carries for the current time and arms the next one. Declared in the manifest, not exported. */
class LiveSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val plan = intent.getStringExtra("plan")?.let(LivePlan::parse) ?: return
        LiveSessions.apply(context, plan)
    }
}
