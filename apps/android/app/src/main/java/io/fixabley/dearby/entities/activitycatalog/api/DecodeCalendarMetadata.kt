package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.ActivityApplication
import io.fixabley.dearby.entities.activitycatalog.model.ActivityPhase
import org.json.JSONObject

internal fun decodeActivityApplication(json: JSONObject) = ActivityApplication(
    json.getString("summary"), json.calendarDate("opensAt"), json.calendarDate("opensOn"),
    json.calendarDate("closesAt"), json.calendarDate("closesOn"), json.calendarTimezone(),
    json.opt("url") as? String, json.stringValues("channels"),
    json.stringValues("requiredDocuments"), json.stringValues("submissionLocations"),
)

internal fun decodeActivityPhase(json: JSONObject) = ActivityPhase(
    json.getString("phase"), json.calendarDate("startsAt"), json.calendarDate("startsOn"),
    json.calendarDate("endsAt"), json.calendarDate("endsOn"), json.calendarTimezone(),
    json.opt("mode") as? String ?: "unknown", json.opt("onlineUrl") as? String,
)

// Null is unknown; a malformed present type must stay invalid, not enable a date fallback.
private fun JSONObject.calendarDate(key: String): String? =
    if (!has(key) || isNull(key)) null else opt(key) as? String ?: ""

private fun JSONObject.calendarTimezone(): String =
    if (!has("timezone")) "Asia/Seoul" else calendarDate("timezone") ?: ""

private fun JSONObject.stringValues(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return (0 until values.length()).mapNotNull { values.opt(it) as? String }
}
