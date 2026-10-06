package com.dearby.nativeapp.entities.catalog.api

import com.dearby.nativeapp.entities.catalog.model.ActivityModel
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.entities.catalog.model.instant
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** `GET /v1/catalog`. Transport or parsing failure throws, never an empty catalog. Call off the main thread. */
fun fetchCatalog(api: String): List<ActivityModel> {
    val connection = URL("$api/v1/catalog").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        check(connection.responseCode == 200) { "catalog ${connection.responseCode}" }
        return parseCatalog(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

fun parseCatalog(json: String): List<ActivityModel> {
    val body = JSONObject(json)
    val organizations = body.getJSONArray("organizations").objects().associate { it.getString("id") to it.getString("name") }
    return body.getJSONArray("activities").objects().map { activity ->
        ActivityModel(
            activity.getString("id"), activity.getString("title"), activity.getString("summary"),
            organizations[activity.getString("organizationId")], activity.getString("participationType")
                .also { require(it == "registration" || it == "selection") },
            activity.getString("recruitmentStatus"), activity.getBoolean("isRecruiting"), activity.getString("freshness"),
            activity.text("recruitmentStartAt"), activity.text("recruitmentEndAt"), activity.text("sourceCheckedAt"),
            activity.text("validUntil"), activity.getString("dateLabel"), activity.text("location"), activity.text("cost"),
            activity.text("audience"), activity.getJSONArray("roles").let { roles -> List(roles.length()) { roles.getString(it) } },
            activity.getJSONArray("schedules").objects().mapNotNull { schedule ->
                val start = schedule.text("startAt")?.takeIf { instant(it) != null }
                val end = schedule.text("endAt")?.takeIf { instant(it) != null }
                if (start == null || end == null) null
                else ScheduleModel(schedule.getString("title"), start, end, schedule.getString("timeZone"))
            },
            activity.getString("officialUrl"), activity.text("applicationUrl"), activity.getString("sourceNote"),
        )
    }
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONObject.text(name: String): String? = if (isNull(name)) null else getString(name)
