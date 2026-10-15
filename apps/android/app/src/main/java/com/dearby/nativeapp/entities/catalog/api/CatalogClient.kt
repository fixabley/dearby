package com.dearby.nativeapp.entities.catalog.api

import com.dearby.nativeapp.entities.catalog.model.ActivityModel
import com.dearby.nativeapp.entities.catalog.model.ActivityPage
import com.dearby.nativeapp.entities.catalog.model.ApplicationWindow
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.entities.catalog.model.instant
import android.os.LocaleList
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * `GET /v1/activities` (contract "활동 조회"): [query] searches name and description, [cursor] is the previous
 * page's `nextCursor`. Transport or parsing failure throws, never an empty page. Call off the main thread.
 */
fun fetchActivities(api: String, query: String?, cursor: String?): ActivityPage {
    val params = listOfNotNull(query?.let { "q" to it }, cursor?.let { "cursor" to it })
        .joinToString("&") { (name, value) -> name + "=" + URLEncoder.encode(value, "UTF-8") }
    val connection = URL("$api/v1/activities" + if (params.isEmpty()) "" else "?$params").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Language", acceptLanguage(LocaleList.getDefault().toLanguageTags().split(',')))
        check(connection.responseCode == 200) { "activities ${connection.responseCode}" }
        return parseActivities(connection.inputStream.bufferedReader().use { it.readText() })
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
        val schedules = activity.getJSONArray("schedules").objects()
        ActivityModel(
            activity.getLong("id").toString(), activity.getString("name"), activity.getString("description"),
            activity.getJSONObject("organization").getString("name"), activity.getJSONObject("program").getString("name"),
            activity.getString("officialUrl"), activity.text("imageUrl"),
            activity.optJSONObject("application")?.let { ApplicationWindow(it.text("startAt"), it.text("endAt")) },
            period?.text("dateLabel") ?: schedules.firstNotNullOfOrNull { it.text("dateLabel") }.orEmpty(),
            period?.text("address") ?: schedules.firstNotNullOfOrNull { it.text("address") },
            // Only schedules with both readable ends can be compared on a calendar.
            schedules.mapNotNull { schedule ->
                val start = schedule.text("startAt")?.takeIf { instant(it) != null }
                val end = schedule.text("endAt")?.takeIf { instant(it) != null }
                if (start == null || end == null) null
                else ScheduleModel(schedule.getString("title"), start, end, schedule.text("timeZone") ?: "Asia/Seoul")
            },
        )
    }, body.text("nextCursor"))
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
/** Null for a missing, null or blank value. */
private fun JSONObject.text(name: String): String? = if (isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
