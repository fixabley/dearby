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
        val sources = root.getJSONArray("sources").objects().associate { source ->
            val record = ActivitySource(source.getString("id"), source.opt("url") as? String,
                source.opt("kind") as? String, source.opt("checkedAt") as? String,
                source.opt("access") as? String, source.opt("note") as? String)
            record.id to record
        }
        val sourceURLs = sources.mapValues { it.value.url }
        val organizations = root.getJSONArray("organizations").objects().map {
            Organization(it.getString("id"), it.getString("name"),
                if (it.isNull("parentOrganizationId")) null else it.getString("parentOrganizationId"))
        }
        val feed = root.getJSONArray("activities").objects()
            .filter { it.getBoolean("demoVisible") }
            .map { item ->
                val evidence = decodeActivityEvidence(item, sourceURLs)
                val sourceIds = item.getJSONArray("sourceIds").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }
                Notice(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    summary = item.getString("summary"),
                    organizationId = if (item.isNull("favoriteOrganizationId")) null else item.getString("favoriteOrganizationId"),
                    audience = item.getJSONObject("audience").getString("summary"),
                    eligibility = item.getJSONObject("eligibility").getString("summary"),
                    application = decodeActivityApplication(item.getJSONObject("application")),
                    location = decodeActivityLocation(item.getJSONObject("location")),
                    schedule = item.getJSONArray("schedule").objects().map(::decodeActivityPhase),
                    benefits = item.getJSONArray("benefits").objects().map { it.getString("summary") },
                    issues = item.getJSONArray("qualityIssues").objects().map { it.getString("summary") },
                    categoryPath = item.getJSONArray("categoryPath").let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    },
                    contexts = item.getJSONArray("contexts").objects().map {
                        NoticeContext(it.getString("organizationId"), it.getString("role"))
                    },
                    edition = if (item.isNull("edition")) null else item.getInt("edition"),
                    sourceUrl = sourceURLs[sourceIds.firstOrNull()].orEmpty(),
                    organizationLinks = item.optJSONArray("organizationLinks")?.objects()?.map {
                        NoticeContext(it.getString("organizationId"), it.getString("role"))
                    }.orEmpty(),
                    sources = (sourceIds + evidence.map { it.sourceId }).distinct().map { id ->
                        sources[id] ?: ActivitySource(id, null, null, null, null, null)
                    },
                    evidence = evidence,
                )
            }.sortedBy { if (it.organizationId == null) 1 else 0 }
        return ActivityCatalog(root.getString("snapshotAt").take(10), organizations, feed)
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
