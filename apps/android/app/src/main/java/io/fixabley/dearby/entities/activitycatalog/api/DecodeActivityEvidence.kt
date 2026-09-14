package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.ActivityEvidence
import org.json.JSONArray
import org.json.JSONObject

/** Retains each evidence association, including nested venue coordinates and array positions. */
internal fun decodeActivityEvidence(item: JSONObject, sourceURLs: Map<String, String?>): List<ActivityEvidence> {
    val result = mutableListOf<ActivityEvidence>()
    fun visit(value: Any?, path: String) {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().sorted().forEach { key ->
                val child = value.opt(key)
                if ((key == "evidence" || key == "coordinateEvidence") && child is JSONArray) {
                    val field = if (key == "coordinateEvidence") "$path.coordinates" else value.opt("fieldPath") as? String ?: path
                    for (index in 0 until child.length()) {
                        val evidence = child.optJSONObject(index) ?: continue
                        val sourceId = evidence.opt("sourceId") as? String ?: continue
                        val locator = evidence.opt("locator") as? String ?: continue
                        result.add(ActivityEvidence(sourceId, locator, field, sourceURLs[sourceId]))
                    }
                } else visit(child, if (path.isEmpty()) key else "$path.$key")
            }
            is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index), "$path[$index]")
        }
    }
    visit(item, "")
    return result
}
