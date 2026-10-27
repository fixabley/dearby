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
import androidx.core.graphics.drawable.IconCompat
import com.dearby.nativeapp.R
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** One 찜 session of the day's [LivePlan]: [start]/[end] epoch millis, its [track] name `트랙 B` and [room] `그랜드볼룸 B` (both null for a session that spans every track). */
data class LiveEntry(val sessionId: String, val start: Long, val end: Long, val track: String?, val room: String?, val title: String, val scheduleId: String)

/**
 * The notification's content at one instant inside the window; see [LiveSessions.frame]. [moving] is 이동 (between sessions or before the first): the
 * walking icon, the title `12분 뒤 시작 · 그랜드볼룸 B로 이동` and the body session title then track; otherwise the running session's title, then track and `14:50 – 15:30 · 방`.
 */
data class LiveFrame(val current: LiveEntry?, val next: LiveEntry?, val moving: Boolean, val title: String, val lines: List<String>, val chip: String)

/**
 * The 진행 트랙 of the day at one instant, in minutes from the first start: [segments] of the timeline between consecutive session starts·ends
 * (`true` a 찜 session runs, `false` a 공백), [points] at the inner boundaries and the [position] of now (0 before the first start).
 */
data class LiveTimeline(val segments: List<Pair<Int, Boolean>>, val points: List<Int>, val position: Int)

/**
 * The day's 행사 진행 중 plan (2026-10-26 user decision "컨퍼런스 참여 시 다이나믹 아일랜드처럼 표시"): the today activity of 오늘 내 행사 and its 찜 sessions
 * of that Seoul day, start order. The notification shows from 30 minutes before the first ([from]) to the latest end ([until]; a long 찜 may
 * outlast one that starts after it). Serialised into the alarm that redraws it and into the device preferences for the reboot restore.
 */
data class LivePlan(val activityId: String, val activityName: String, val entries: List<LiveEntry>) {
    val from get() = entries.first().start - LiveSessions.LEAD_MS
    val until get() = entries.maxOf { it.end }
    fun toJson(): String = JSONObject().put("activityId", activityId).put("activityName", activityName).put("entries", JSONArray().apply {
        entries.forEach { put(JSONObject().put("sessionId", it.sessionId).put("start", it.start).put("end", it.end).put("track", it.track ?: JSONObject.NULL).put("room", it.room ?: JSONObject.NULL).put("title", it.title).put("scheduleId", it.scheduleId)) }
    }).toString()
    companion object {
        fun parse(json: String): LivePlan? = runCatching {
            val o = JSONObject(json)
            val entries = o.getJSONArray("entries").let { array -> List(array.length()) { i ->
                val e = array.getJSONObject(i)
                fun text(key: String) = if (e.isNull(key)) null else e.optString(key).takeIf { it.isNotBlank() }
                // A plan stored before 2026-10-27 has no `track`·`room`; it is redrawn without them until the app computes the day again.
                LiveEntry(e.getString("sessionId"), e.getLong("start"), e.getLong("end"), text("track"), text("room"), e.getString("title"), e.getString("scheduleId"))
            } }
            LivePlan(o.getString("activityId"), o.getString("activityName"), entries).takeIf { entries.isNotEmpty() }
        }.getOrNull()
    }
}

/**
 * 행사 당일 진행 중 알림 (2026-10-26, device only, no server part): one ongoing notification on the `행사 진행 중` channel while the today activity's 찜
 * sessions run — since 2026-10-27 drawn like 내 활동's 진행 트랙: 세션 중 the session, its track, `14:50 – 15:30 · 방`; 이동 between sessions the
 * walking icon and `12분 뒤 시작 · 방으로 이동` first ([frame]); the activity in the header. On Android 16+ it is a live update (`ProgressStyle` with a
 * segment per session and 공백, [timeline]; promoted ongoing: status bar chip, One UI 8 Now Bar); below, an ordinary ongoing notification with a `●●◉○○` line. The channel is (re)created before every post, since an alarm or the boot restore may run before the Activity.
 * It is redrawn by one `AlarmManager` alarm at the next change — the window start, then every minute so the chip's minutes count down — and at every [apply] from the app (foreground, 찜 change, switch). Exact alarms follow the `알람 및 리마인더`
 * allowance like 세션 이동 알림; without it the system may redraw a few minutes late. After the last session ends everything is removed.
 * A tap or `내 시간표 보기` opens the activity and that block's timetable on 내 시간표 with the 세션 이동 알림 payload (`kind=session_reminder`), which MainActivity already handles.
 */
object LiveSessions {
    const val CHANNEL = "live_session"
    internal const val LEAD_MS = 30 * 60_000L
    private const val MINUTE = 60_000L
    /** 내 활동 진행 트랙: the past segment `TealFill` and the upcoming `surfaceContainerHighest` (shared/ui Components, light). */
    private const val TEAL = 0xFF007F80.toInt()
    private const val GAP = 0xFFD9E5E4.toInt()
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
                it.track?.name?.takeIf(String::isNotBlank), it.track?.room?.takeIf(String::isNotBlank), it.session.title, it.scheduleId) }
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
        frame(plan, now)?.let { notify(context, plan, it, now) } ?: notifications.cancel(ID)
        val fire = PendingIntent.getBroadcast(context, ID, intent.putExtra(PLAN, plan.toJson()), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true
        if (exact) alarms?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, fire) else alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, fire)
        return true
    }

    /**
     * When the alarm should redraw after [now]: the window start before it; inside, the next whole minute before what the chip counts to (the running
     * session's end, else the next start), so `12분` turns exactly and the progress moves — every start and end is such a minute; null once every session ended.
     */
    internal fun nextAlarm(plan: LivePlan, now: Long): Long? {
        if (now >= plan.until) return null
        if (now < plan.from) return plan.from
        val target = frame(plan, now)?.let { it.current?.end ?: it.next?.start } ?: plan.until
        return now + (target - now - 1).mod(MINUTE) + 1
    }

    /**
     * What the notification shows at [now] (2026-10-27 user decision "내 활동 진행 트랙처럼, 이동 시간 강조"): the running session ([current]; of two 찜
     * starting together, the first in 내 시간표 order) and the first one starting later ([next]). During a session the title is its name, the [lines] its
     * track and `14:50 – 15:30 · 그랜드볼룸 B`, the [chip] the minutes until it ends `12분`. Otherwise (between sessions or before the first, whatever the room — a
     * conference is mostly one venue, so "same room" is not judged) it is 이동: the title `12분 뒤 시작 · 그랜드볼룸 B로 이동` (the track without a room,
     * only `12분 뒤 시작` without either), the lines the next session's name and track, the chip the minutes until it starts. The chip (status bar,
     * Now Bar collapsed) is always whole minutes — `65분`, not `1시간 5분`; `1분` under a minute. Null outside the window.
     */
    internal fun frame(plan: LivePlan, now: Long): LiveFrame? {
        if (now < plan.from || now >= plan.until) return null
        val current = plan.entries.firstOrNull { it.start <= now && now < it.end }
        val next = plan.entries.firstOrNull { it.start > now }
        if (current != null) return LiveFrame(current, next, false, current.title,
            listOfNotNull(current.track, listOfNotNull("${time(current.start)} – ${time(current.end)}", current.room).joinToString(" · ")), minutes(current.end - now))
        // No session runs and the window is open, so one starts later (the window ends with the latest end).
        val upcoming = next ?: return null
        val title = listOfNotNull("${left(upcoming.start - now)} 뒤 시작", (upcoming.room ?: upcoming.track)?.let { "${toward(it)} 이동" }).joinToString(" · ")
        return LiveFrame(null, upcoming, true, title, listOfNotNull(upcoming.title, upcoming.track), minutes(upcoming.start - now))
    }

    /** The day's 진행 트랙 at [now]; see [LiveTimeline]. Overlapping 찜 cut each other's segments, so every start and end is a boundary. */
    internal fun timeline(plan: LivePlan, now: Long): LiveTimeline {
        val first = plan.entries.first().start
        fun minutes(at: Long) = ((at - first) / MINUTE).toInt()
        val bounds = plan.entries.flatMap { listOf(it.start, it.end) }.distinct().sorted()
        val segments = bounds.zipWithNext().mapNotNull { (from, to) ->
            (minutes(to) - minutes(from)).takeIf { it > 0 }?.let { it to plan.entries.any { e -> e.start <= from && to <= e.end } }
        }
        return LiveTimeline(segments, bounds.drop(1).dropLast(1).map(::minutes), minutes(now.coerceIn(first, plan.until)))
    }

    /** Below Android 16, the 진행 트랙 as one line: `●` an ended 찜, `◉` a running one, `○` one to come — `●●◉○○`. */
    internal fun steps(plan: LivePlan, now: Long) = plan.entries.joinToString("") { when { it.end <= now -> "●"; it.start <= now -> "◉"; else -> "○" } }

    private fun notify(context: Context, plan: LivePlan, frame: LiveFrame, now: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = frame.lines.joinToString("\n")
        // The 세션 이동 알림 payload (features/reminder `kind`·`activityId`·`scheduleId`·`sessionId`): MainActivity opens the activity and the block on 내 시간표.
        val target = frame.current ?: frame.next ?: plan.entries.last()
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .putExtra("kind", "session_reminder").putExtra("activityId", plan.activityId).putExtra("scheduleId", target.scheduleId).putExtra("sessionId", target.sessionId)
        val tap = PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        createChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(if (frame.moving) R.drawable.ic_live_walk else R.drawable.dearby_symbol)
            .setContentTitle(frame.title).setContentText(text).setSubText(plan.activityName)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_EVENT).setContentIntent(tap)
            .addAction(0, "내 시간표 보기", tap)
            .setShowWhen(false).setRequestPromotedOngoing(true).setShortCriticalText(frame.chip)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Android 16+: the live update's own progress style (Now Bar) as 내 활동's 진행 트랙 — 찜 sessions teal (`TealFill`), 공백 the track's
            // upcoming tone, the part after now faded by the system; the walking person rides the track while 이동.
            val line = timeline(plan, now)
            val style = NotificationCompat.ProgressStyle()
                .setProgressSegments(line.segments.map { (length, session) -> NotificationCompat.ProgressStyle.Segment(length).setColor(if (session) TEAL else GAP) })
                .setProgressPoints(line.points.map { NotificationCompat.ProgressStyle.Point(it).setColor(TEAL) }).setProgress(line.position)
            if (frame.moving) style.setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_walk))
            builder.setStyle(style)
        } else builder.setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + steps(plan, now)))
        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }

    /**
     * Debug 미리보기 (설정 › 알림 `행사 진행 중 표시 미리보기`, 2026-10-27): a made-up plan to see the live update without an event today — `Dearby 미리보기`
     * with 트랙 B's session starting 12 minutes after [now] for 40 minutes, and 트랙 A's before it: running since 20 minutes before until 7 minutes after
     * [now] (세션 중), or ended 3 minutes before it when [moving] (이동). Shown through [apply] like a real plan, so its own alarm redraws it (the 세션 중
     * one turns 이동 as it runs) and finally removes it; it is never stored, so a reboot does not restore it.
     */
    fun preview(moving: Boolean, now: Long = System.currentTimeMillis()): LivePlan {
        val firstEnd = now + (if (moving) -3 else 7) * MINUTE
        return LivePlan("preview", "Dearby 미리보기", listOf(
            LiveEntry("preview-a", firstEnd - 27 * MINUTE, firstEnd, "트랙 A", "그랜드볼룸 A", "라이브 업데이트로 보는 행사 진행", "preview"),
            LiveEntry("preview-b", now + 12 * MINUTE, now + 52 * MINUTE, "트랙 B", "그랜드볼룸 B", "다음 세션 미리보기", "preview")))
    }
    /** Debug only: the 미리보기 shown — null none, false 세션 중, true 이동; each tap of the row goes none → 세션 중 → 이동 → none. */
    var previewMoving: Boolean? = null
    /** Debug only: while true, MainActivity's day plan leaves the preview alone instead of applying (and usually removing) the real plan. */
    val previewing get() = previewMoving != null

    private fun time(at: Long) = clock.format(Instant.ofEpochMilli(at).atZone(seoul))
    /** `그랜드볼룸 B로`, `강당으로`: 으로 after a final consonant other than ㄹ (Hangul, or a digit·letter read 영·삼·육·엠·엔), else 로. */
    private fun toward(place: String): String {
        val last = place.trimEnd().lastOrNull() ?: return place
        val batchim = if (last in '가'..'힣') (last - '가') % 28 else -1
        val euro = if (batchim >= 0) batchim != 0 && batchim != 8 else last.uppercaseChar() in "036MN"
        return place.trimEnd() + if (euro) "으로" else "로"
    }
    /** The chip's `12분`, `65분`: whole minutes rounded up, never under one. */
    private fun minutes(ms: Long) = "${((ms + MINUTE - 1) / MINUTE).coerceAtLeast(1)}분"
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
