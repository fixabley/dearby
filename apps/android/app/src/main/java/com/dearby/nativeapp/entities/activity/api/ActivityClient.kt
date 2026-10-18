package com.dearby.nativeapp.entities.activity.api

import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.EligibilityCondition
import com.dearby.nativeapp.entities.activity.model.EligibilityGroup
import com.dearby.nativeapp.entities.activity.model.ActivityPage
import com.dearby.nativeapp.entities.activity.model.ApplicationWindow
import com.dearby.nativeapp.entities.activity.model.ProgramPage
import com.dearby.nativeapp.entities.activity.model.ProgramSummary
import com.dearby.nativeapp.entities.activity.model.StageSchedule
import com.dearby.nativeapp.entities.activity.model.instant
import android.os.LocaleList
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * `GET /v1/activities` (contract "활동 조회"): [query] searches name and description, [cursor] is the previous
 * page's `nextCursor`, [programId] narrows to one program (contract "발견 프로그램 묶음"). Transport or parsing
 * failure throws, never an empty page. Call off the main thread.
 */
fun fetchActivities(api: String, query: String?, cursor: String?, programId: String? = null): ActivityPage =
    parseActivities(get(api, "activities", listOfNotNull(query?.let { "q" to it }, cursor?.let { "cursor" to it }, programId?.let { "programId" to it })))

/**
 * `GET /v1/discovery/programs` (contract "발견 프로그램 묶음"): programs that have at least one open activity, with the
 * count, nearest deadline (UTC ISO 8601 or null) and the activities whose name matched [query]. [participationType] is
 * REGISTRATION or SELECTION; [cursor] is the previous page's integer offset `nextCursor`, carried as text.
 */
fun fetchPrograms(api: String, query: String?, participationType: String?, cursor: String?): ProgramPage =
    parsePrograms(get(api, "discovery/programs", listOfNotNull(query?.let { "q" to it }, participationType?.let { "participationType" to it }, cursor?.let { "cursor" to it })))

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
        check(connection.responseCode == 200) { "$path ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** The device language order, so the API answers activity text in English when English comes before Korean. */
fun acceptLanguage(tags: List<String>): String =
    tags.filter { it.isNotBlank() }.take(5).mapIndexed { index, tag -> if (index == 0) tag else "$tag;q=0.${10 - index}" }
        .joinToString(", ").ifEmpty { "ko" }

fun parseActivities(json: String): ActivityPage {
    val body = JSONObject(json)
    return ActivityPage(body.getJSONArray("items").objects().map { activity ->
        val period = activity.optJSONObject("activitySchedule")
        val raw = activity.getJSONArray("schedules").objects()
        val schedules = raw.map { it.stageSchedule() }
        ActivityModel(
            activity.getLong("id").toString(), activity.getString("name"), activity.getString("description"),
            activity.getJSONObject("organization").getString("name"), activity.getJSONObject("program").getString("name"),
            activity.getString("officialUrl"), activity.text("imageUrl"),
            activity.optJSONObject("application")?.let { ApplicationWindow(it.text("startAt"), it.text("endAt")) },
            period?.text("dateLabel") ?: schedules.firstNotNullOfOrNull { it.dateLabel.ifBlank { null } }.orEmpty(),
            period?.text("address") ?: raw.firstNotNullOfOrNull { it.text("address") },
            schedules, period?.stageSchedule(),
            // An unreadable eligibility never fails the activity.
            activity.optJSONObject("eligibility")?.let { runCatching { it.eligibilityGroup(0) }.getOrNull() },
        )
    }, body.text("nextCursor"))
}

/** Only readable times count; a schedule may have none (all-day or undated). */
private fun JSONObject.stageSchedule() = StageSchedule(
    optString("title"), text("kind") ?: "EVENT", text("startAt")?.takeIf { instant(it) != null }, text("endAt")?.takeIf { instant(it) != null },
    text("timeZone") ?: "Asia/Seoul", text("startDate"), text("endDateExclusive"), text("dateLabel").orEmpty(),
)

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

fun parsePrograms(json: String): ProgramPage {
    val body = JSONObject(json)
    return ProgramPage(body.getJSONArray("items").objects().map { program ->
        val matched = program.optJSONArray("matchedActivityIds")
        ProgramSummary(
            program.getLong("id").toString(), program.getString("name"), program.text("nameEn"),
            program.getJSONObject("organization").getString("name"), program.getInt("openActivityCount"),
            program.text("nearestRecruitmentEndAt"), if (matched == null) emptyList() else List(matched.length()) { matched.getLong(it).toString() },
        )
    }, if (body.isNull("nextCursor")) null else body.getLong("nextCursor").toString())
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
/** Null for a missing, null or blank value. */
private fun JSONObject.text(name: String): String? = if (isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
