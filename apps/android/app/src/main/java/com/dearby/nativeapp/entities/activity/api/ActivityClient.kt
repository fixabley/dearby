package com.dearby.nativeapp.entities.activity.api

import com.dearby.nativeapp.entities.activity.model.ActivityCategory
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.EligibilityCondition
import com.dearby.nativeapp.entities.activity.model.FavoriteActivity
import com.dearby.nativeapp.entities.activity.model.FavoriteProgram
import com.dearby.nativeapp.entities.activity.model.Favorites
import com.dearby.nativeapp.entities.activity.model.InboxGroup
import com.dearby.nativeapp.entities.activity.model.InboxItem
import com.dearby.nativeapp.entities.activity.model.InboxPage
import com.dearby.nativeapp.entities.activity.model.NotifyLevel
import com.dearby.nativeapp.entities.activity.model.EligibilityGroup
import com.dearby.nativeapp.entities.activity.model.Highlight
import com.dearby.nativeapp.entities.activity.model.ActivityPage
import com.dearby.nativeapp.entities.activity.model.ApplicationWindow
import com.dearby.nativeapp.entities.activity.model.Place
import com.dearby.nativeapp.entities.activity.model.ProgramPage
import com.dearby.nativeapp.entities.activity.model.ProgramSummary
import com.dearby.nativeapp.entities.activity.model.QueueItem
import com.dearby.nativeapp.entities.activity.model.QueueScene
import com.dearby.nativeapp.entities.activity.model.ScheduleStatus
import com.dearby.nativeapp.entities.activity.model.StageSchedule
import com.dearby.nativeapp.entities.activity.model.Session
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import com.dearby.nativeapp.entities.activity.model.Speaker
import com.dearby.nativeapp.entities.activity.model.Timetable
import com.dearby.nativeapp.entities.activity.model.Track
import java.time.OffsetDateTime
import com.dearby.nativeapp.entities.activity.model.Tag
import com.dearby.nativeapp.entities.activity.model.TagKind
import com.dearby.nativeapp.entities.activity.model.instant
import android.os.LocaleList
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * `GET /v1/activities` (contract "활동 조회"): [query] searches name and description, [cursor] is the previous
 * page's `nextCursor`, [programId] narrows to one program (contract "발견 프로그램 묶음"), [open] asks the server for
 * open activities only (contract "열린 활동 판정 일원화", 2026-10-19: the client never judges openness itself). Transport or
 * parsing failure throws, never an empty page. Call off the main thread.
 */
fun fetchActivities(api: String, query: String?, cursor: String?, programId: String? = null, open: Boolean = false, kind: String? = null): ActivityPage =
    parseActivities(get(api, "activities", listOfNotNull(query?.let { "q" to it }, cursor?.let { "cursor" to it }, programId?.let { "programId" to it }, if (open) "open" to "true" else null, kind?.let { "kind" to it })))

/**
 * `GET /v1/discovery/programs` (contract "발견 프로그램 묶음"): programs that have at least one open activity, with the
 * count, nearest deadline (UTC ISO 8601 or null) and the activities whose name matched [query]. [participationType] is
 * REGISTRATION or SELECTION; [cursor] is the previous page's integer offset `nextCursor`, carried as text. [openOnly] false
 * (contract "모집 중인 프로그램만 보기") also returns programs that had an activity in the last three years, open ones first.
 * [query] also matches tag names (contract "태그 칩 줄 제거", 2026-10-19 밤: tags are a search condition only; the app sends no tag filter).
 * The program logo path is resolved against [api] (contract "프로그램 로고"). [kind] `program`|`event` (contract "프로그램·이벤트 전환", 2026-10-26)
 * narrows to that kind's activities on both this call and [fetchActivities]; a server without it ignores the parameter and lists everything.
 */
fun fetchPrograms(api: String, query: String?, participationType: String?, cursor: String?, openOnly: Boolean = true, kind: String? = null): ProgramPage =
    parsePrograms(get(api, "discovery/programs", listOfNotNull(query?.let { "q" to it }, participationType?.let { "participationType" to it }, cursor?.let { "cursor" to it }, if (openOnly) null else "openOnly" to "false", kind?.let { "kind" to it })), api)

private fun get(api: String, path: String, params: List<Pair<String, String>>): String {
    val query = params.joinToString("&") { (name, value) -> name + "=" + URLEncoder.encode(value, "UTF-8") }
    val connection = URL("$api/v1/$path" + if (query.isEmpty()) "" else "?$query").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Language", acceptLanguage(LocaleList.getDefault().toLanguageTags().split(',')))
        if (connection.responseCode == 404) throw NotFound()
        check(connection.responseCode == 200) { "$path ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private class NotFound : Exception()

/** The device language order, so the API answers activity text in English when English comes before Korean. */
fun acceptLanguage(tags: List<String>): String =
    tags.filter { it.isNotBlank() }.take(5).mapIndexed { index, tag -> if (index == 0) tag else "$tag;q=0.${10 - index}" }
        .joinToString(", ").ifEmpty { "ko" }

/**
 * `GET /v1/activities/:id` (contract "활동 조회"): one published activity, or null when it is gone (404) — a 내 활동 record whose
 * activity was deleted shows as "삭제된 활동" (contract "내 활동 서버 저장"). Other failures throw. Call off the main thread.
 */
fun fetchActivity(api: String, id: String): ActivityModel? = try {
    parseActivity(JSONObject(get(api, "activities/$id", emptyList())))
} catch (e: NotFound) { null }

/**
 * `GET /v1/activities/:id/schedules/:scheduleId/timetable` (contract "트랙·세션 시간표"), read without a token: the hearts come from
 * `GET /v1/me/session-favorites`. Null when the block has no timetable or is gone (404); other failures throw. Call off the main thread.
 */
fun fetchTimetable(api: String, activityId: String, scheduleId: String): Timetable? = try {
    parseTimetable(JSONObject(get(api, "activities/$activityId/schedules/$scheduleId/timetable", emptyList())))
} catch (e: NotFound) { null }

/** Tracks are put in `position` order; an unreadable session is skipped. */
fun parseTimetable(body: JSONObject) = Timetable(
    body.get("activityId").toString(), body.optJSONObject("schedule")?.optString("title").orEmpty(),
    body.optJSONArray("tracks")?.objects()?.mapNotNull { runCatching { Track(it.get("id").toString(), it.getString("name"), it.text("room"), it.optInt("position")) }.getOrNull() }.orEmpty().sortedBy { it.position },
    body.optJSONArray("sessions")?.objects()?.mapNotNull { runCatching { it.session() }.getOrNull() }.orEmpty(),
)

/**
 * `GET /v1/me/session-favorites` → `{items: [{activityId, activityName, scheduleId, scheduleTitle, track, session}]}`; `track` (2026-10-26) is null for a
 * session spanning every track. An unreadable item is skipped.
 */
fun parseSessionFavorites(body: JSONObject): List<SessionFavorite> = body.optJSONArray("items")?.objects()?.mapNotNull { item ->
    runCatching {
        SessionFavorite(item.get("activityId").toString(), item.optString("activityName"), item.get("scheduleId").toString(), item.optString("scheduleTitle"), item.getJSONObject("session").session(),
            item.optJSONObject("track")?.let { Track(it.get("id").toString(), it.getString("name"), it.text("room"), it.optInt("position")) })
    }.getOrNull()
}.orEmpty()

private fun JSONObject.session() = Session(
    get("id").toString(), get("scheduleId").toString(), if (isNull("trackId") || !has("trackId")) null else get("trackId").toString(), text("kind") ?: "TALK",
    getString("title"), text("description"), OffsetDateTime.parse(getString("startAt")), OffsetDateTime.parse(getString("endAt")),
    optJSONArray("speakers")?.objects()?.mapNotNull { runCatching { Speaker(it.getString("name"), it.text("affiliation")) }.getOrNull() }.orEmpty(),
    tags("tags"),
)

fun parseActivities(json: String): ActivityPage {
    val body = JSONObject(json)
    return ActivityPage(body.getJSONArray("items").objects().map(::parseActivity), body.text("nextCursor"))
}

private fun parseActivity(activity: JSONObject): ActivityModel {
    val period = activity.optJSONObject("activitySchedule")
    val raw = activity.getJSONArray("schedules").objects()
    val schedules = raw.map { it.stageSchedule() }
    return ActivityModel(
        activity.getLong("id").toString(), activity.getString("name"), activity.getString("description"),
        activity.getJSONObject("organization").getString("name"), activity.getJSONObject("program").getString("name"),
        activity.getString("officialUrl"), activity.text("imageUrl"),
        activity.optJSONObject("application")?.let { ApplicationWindow(it.text("startAt"), it.text("endAt"), it.text("startDate"), it.text("endDateExclusive"), it.idText(), it.statuses()) },
        period?.text("dateLabel") ?: schedules.firstNotNullOfOrNull { it.dateLabel.ifBlank { null } }.orEmpty(),
        period?.text("address") ?: raw.firstNotNullOfOrNull { it.text("address") },
        schedules, period?.stageSchedule(),
        // An unreadable eligibility never fails the activity.
        activity.optJSONObject("eligibility")?.let { runCatching { it.eligibilityGroup(0) }.getOrNull() },
        ActivityCategory.fromCode(activity.text("category")),
        activity.tags("fieldTags"), activity.traitTags(), activity.tags("fieldTagsAll"),
        activity.text("location"),
        // A malformed place (contract "활동 장소": four fields together) never fails the activity.
        activity.optJSONObject("place")?.let { runCatching { Place(it.getString("name"), it.getString("address"), it.getDouble("lat"), it.getDouble("lng")) }.getOrNull() },
        activity.text("applicationUrl"),
        activity.text("hook"),
        activity.highlights("highlights"),
    )
}

/** Only readable times count; a schedule may have none (all-day or undated). */
private fun JSONObject.stageSchedule() = StageSchedule(
    optString("title"), text("kind") ?: "EVENT", text("startAt")?.takeIf { instant(it) != null }, text("endAt")?.takeIf { instant(it) != null },
    text("timeZone") ?: "Asia/Seoul", text("startDate"), text("endDateExclusive"), text("dateLabel").orEmpty(),
    idText(), statuses(), optJSONObject("overlapCheck")?.text("startTime"), optJSONObject("overlapCheck")?.text("endTime"),
    text("recurrence"), optBoolean("hasTimetable", false),
)

/** Contract "내 활동 서버 저장·일정 상태": `statuses [{id?, name, kind}]`; an unreadable entry is skipped, none → the defaults downstream. */
private fun JSONObject.statuses(): List<ScheduleStatus> = optJSONArray("statuses")?.objects()?.mapNotNull { status ->
    runCatching { ScheduleStatus(status.idText(), status.getString("name"), status.text("kind") ?: "OTHER") }.getOrNull()
} ?: emptyList()
private fun JSONObject.idText(): String? = if (isNull("id")) null else get("id").toString()

/** Contract "대상자 조건": camelCase keys (`field`, `fieldEn`, `from`, `to`, `values`, `valuesEn`, `note`, `noteEn`), groups nest at most 3 deep; anything deeper or malformed is dropped. */
private fun JSONObject.eligibilityGroup(depth: Int): EligibilityGroup? {
    if (depth > 2 || !has("items")) return null
    val items = getJSONArray("items").objects().mapNotNull { item ->
        if (item.has("items")) item.eligibilityGroup(depth + 1)
        else EligibilityCondition(
            item.getString("field"), item.optString("fieldEn"),
            if (item.isNull("from")) null else item.getDouble("from"), if (item.isNull("to")) null else item.getDouble("to"),
            item.optJSONArray("values")?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList(), item.text("note"),
        )
    }
    return EligibilityGroup(if (optString("op") == "any") "any" else "all", items)
}

/** [api] is the origin the `logoUrl` path (`/v1/programs/{id}/logo?v=`) is resolved against; an unreadable path leaves no logo. */
fun parsePrograms(json: String, api: String): ProgramPage {
    val body = JSONObject(json)
    return ProgramPage(body.getJSONArray("items").objects().map { it.programSummary(api) }, if (body.isNull("nextCursor")) null else body.getLong("nextCursor").toString())
}

/** The program's paths (`logoUrl`, `coverUrl`) are resolved against [api]; counts an item leaves out (a 추천·대기열 program) read as 0. */
private fun JSONObject.programSummary(api: String): ProgramSummary {
    val matched = optJSONArray("matchedActivityIds")
    fun path(key: String) = text(key)?.let { runCatching { URI(api).resolve(it).toString() }.getOrNull() }
    return ProgramSummary(
        getLong("id").toString(), getString("name"), text("nameEn"),
        optJSONObject("organization")?.text("name").orEmpty(), optInt("openActivityCount", 0),
        text("nearestRecruitmentEndAt"), if (matched == null) emptyList() else List(matched.length()) { matched.getLong(it).toString() },
        text("latestActivityAt"),
        upcomingActivityCount = optInt("upcomingActivityCount", 0), nearestRecruitmentStartAt = text("nearestRecruitmentStartAt"),
        nearestIsActivityStart = optBoolean("nearestIsActivityStart", false),
        tags("fieldTags"), traitTags(), logoUrl = path("logoUrl"), coverUrl = path("coverUrl"),
    )
}

/**
 * 오늘의 맞춤 대기열 (`GET /v1/me/discovery-queue`, `POST …/refill`) and the 추천 API (`/v1/discovery/recommendations`) share the item shape
 * `{items: [{activity, program, reasons}]}` (contract "맞춤 대기열"); an unreadable item is skipped. A missing `program` falls back to the activity's.
 */
fun parseQueue(body: JSONObject, api: String): List<QueueItem> = body.getJSONArray("items").objects().mapNotNull { item ->
    runCatching {
        val activity = item.getJSONObject("activity")
        val program = item.optJSONObject("program") ?: activity.getJSONObject("program")
        QueueItem(parseActivity(activity), program.programSummary(api), item.strings("reasons"),
            item.optJSONArray("scenes")?.objects()?.mapNotNull { runCatching { it.scene() }.getOrNull() }?.ifEmpty { null })
    }.getOrNull()
}

/**
 * 즐겨찾기 (`GET /v1/me/favorites`, contract "즐겨찾기(하트)·새 모집 푸시 알림"): `{programs: [{program, favoritedAt, newRecruitment, notifyLevel}],
 * activities: [{activity, program, favoritedAt, notifyLevel}]}`; an unreadable entry is skipped, a missing `program` falls back to the activity's.
 */
fun parseFavorites(body: JSONObject, api: String): Favorites = Favorites(
    body.optJSONArray("programs")?.objects()?.mapNotNull { item ->
        runCatching { FavoriteProgram(item.getJSONObject("program").programSummary(api), item.text("favoritedAt"), item.optBoolean("newRecruitment"), NotifyLevel.of(item.text("notifyLevel"))) }.getOrNull()
    }.orEmpty(),
    body.optJSONArray("activities")?.objects()?.mapNotNull { item ->
        runCatching {
            val activity = item.getJSONObject("activity")
            FavoriteActivity(parseActivity(activity), (item.optJSONObject("program") ?: activity.getJSONObject("program")).programSummary(api), item.text("favoritedAt"), NotifyLevel.of(item.text("notifyLevel")))
        }.getOrNull()
    }.orEmpty(),
)

/** 알림 목록 (`GET /v1/me/notifications`): an unreadable group or item is skipped; `nextCursor` is passed back as text. */
fun parseInbox(body: JSONObject, api: String): InboxPage = InboxPage(
    body.optJSONArray("groups")?.objects()?.mapNotNull { group ->
        runCatching {
            InboxGroup(group.getJSONObject("program").programSummary(api), group.optInt("unread", 0), group.getJSONArray("items").objects().mapNotNull { item ->
                runCatching {
                    val activity = item.getJSONObject("activity")
                    InboxItem(item.get("id").toString(), item.getString("kind"), activity.get("id").toString(), activity.getString("name"), instant(item.text("createdAt")), !item.isNull("readAt") && item.has("readAt"))
                }.getOrNull()
            })
        }.getOrNull()
    }.orEmpty(),
    if (body.isNull("nextCursor") || !body.has("nextCursor")) null else body.get("nextCursor").toString(),
)

/** One server scene; an unknown `type` (or an unreadable one) is null and skipped. `dday` is days left (a `D-22`/`D-DAY` text reads too). */
private fun JSONObject.scene(): QueueScene? = when (text("type")) {
    "HOOK" -> QueueScene.Hook(getString("hook"), text("name"))
    "HIGHLIGHTS" -> highlights("items").takeIf { it.isNotEmpty() }?.let(QueueScene::Highlights)
    "ELIGIBILITY" -> QueueScene.Eligibility(text("text"), highlights("items")).takeIf { it.text != null || it.items.isNotEmpty() }
    "BENEFIT" -> QueueScene.Benefit(getString("text"))
    "DEADLINE" -> QueueScene.Deadline(text("label"),
        when (val dday: Any? = opt("dday")) { is Number -> dday.toInt(); is String -> if (dday.equals("D-DAY", true)) 0 else dday.removePrefix("D-").toIntOrNull(); else -> null },
        text("deadlineText"), strings("reasons"))
    else -> null
}
/** `[{icon, text}]`; an entry without text is skipped (the icon is read by the app, unknown ones fall back there). */
private fun JSONObject.highlights(key: String): List<Highlight> =
    optJSONArray(key)?.objects()?.mapNotNull { item -> item.text("text")?.let { Highlight(item.text("icon") ?: "", it) } }.orEmpty()
private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { r -> List(r.length()) { r.getString(it) } }.orEmpty()

/** Contract "분야·성격 태그와 아이콘": every tag array is optional (older API) and an unreadable entry is skipped. */
private fun JSONObject.tags(key: String): List<Tag> = optJSONArray(key)?.objects()?.mapNotNull { runCatching { it.tag() }.getOrNull() } ?: emptyList()
/** An older API has no `traitTags`; its `tags` stand in as 성격. */
private fun JSONObject.traitTags() = tags(if (has("traitTags")) "traitTags" else "tags")
private fun JSONObject.tag() = Tag(getLong("id").toString(), getString("name"), text("nameEn"), if (text("kind") == "FIELD") TagKind.FIELD else TagKind.TRAIT)

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
/** Null for a missing, null or blank value. */
private fun JSONObject.text(name: String): String? = if (isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
