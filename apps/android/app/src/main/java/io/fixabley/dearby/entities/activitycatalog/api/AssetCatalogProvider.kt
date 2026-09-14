package io.fixabley.dearby.entities.activitycatalog.api

import android.content.res.AssetManager
import io.fixabley.dearby.entities.activitycatalog.model.*
import org.json.JSONArray
import org.json.JSONObject

internal class AssetCatalogProvider(private val assets: AssetManager) : CatalogProvider {
    override fun load(): ActivityCatalog {
        val raw = assets.open("activity-samples.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        check(root.getString("schemaVersion") == "1.0.0")
        check(root.getString("mode") == "reviewed_sample")
        val sources = root.getJSONArray("sources").objects().associate {
            it.getString("id") to it.getString("url")
        }
        val organizations = root.getJSONArray("organizations").objects().map {
            Organization(it.getString("id"), it.getString("name"),
                if (it.isNull("parentOrganizationId")) null else it.getString("parentOrganizationId"))
        }
        val feed = root.getJSONArray("activities").objects()
            .filter { it.getBoolean("demoVisible") }
            .map { item ->
                Notice(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    summary = item.getString("summary"),
                    organizationId = if (item.isNull("favoriteOrganizationId")) null else item.getString("favoriteOrganizationId"),
                    audience = item.getJSONObject("audience").getString("summary"),
                    eligibility = item.getJSONObject("eligibility").getString("summary"),
                    application = item.getJSONObject("application").getString("summary"),
                    location = decodeActivityLocation(item.getJSONObject("location")),
                    schedule = item.getJSONArray("schedule").objects().map { phase ->
                        val label = mapOf("event" to "행사", "preliminary" to "예선", "finalist_announcement" to "결선 진출 발표", "final" to "결선·시상")[phase.getString("phase")] ?: phase.getString("phase")
                        val start = if (phase.isNull("startsAt")) phase.optString("startsOn", "일정 미확인") else phase.getString("startsAt").take(16).replace('T', ' ')
                        val end = if (phase.isNull("endsAt")) "" else " ~ " + phase.getString("endsAt").take(16).replace('T', ' ')
                        "$label: $start$end (한국 시간)"
                    },
                    benefits = item.getJSONArray("benefits").objects().map { it.getString("summary") },
                    issues = item.getJSONArray("qualityIssues").objects().map { it.getString("summary") },
                    categoryPath = item.getJSONArray("categoryPath").let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    },
                    contexts = item.getJSONArray("contexts").objects().map {
                        NoticeContext(it.getString("organizationId"), it.getString("role"))
                    },
                    edition = if (item.isNull("edition")) null else item.getInt("edition"),
                    sourceUrl = sources.getValue(item.getJSONArray("sourceIds").getString(0)),
                )
            }.sortedBy { if (it.organizationId == null) 1 else 0 }
        return ActivityCatalog(root.getString("snapshotAt").take(10), organizations, feed)
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
