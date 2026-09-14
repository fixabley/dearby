package io.fixabley.dearby.app.data

import io.fixabley.dearby.entities.notice.model.NoticeApplication
import io.fixabley.dearby.entities.notice.model.NoticePhase
import org.json.JSONObject

internal fun decodeNoticeApplication(json: JSONObject) = NoticeApplication(
    json.getString("summary"), json.calendarDate("opensAt"), json.calendarDate("opensOn"),
    json.calendarDate("closesAt"), json.calendarDate("closesOn"), json.calendarTimezone(),
    json.opt("url") as? String, json.stringValues("channels"),
    json.stringValues("requiredDocuments"), json.stringValues("submissionLocations"),
)

internal fun decodeNoticePhase(json: JSONObject) = NoticePhase(
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
