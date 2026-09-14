package io.fixabley.dearby.app.data

import io.fixabley.dearby.entities.organization.model.OrganizationModel
import android.content.res.AssetManager
import io.fixabley.dearby.entities.notice.model.NoticeSource
import io.fixabley.dearby.entities.notice.model.NoticeModel
import io.fixabley.dearby.entities.notice.model.NoticeContext
import org.json.JSONArray
import org.json.JSONObject

internal class AssetNoticeSnapshotReader(private val assets: AssetManager) : NoticeSnapshotReader {
    override fun load(): NoticeSnapshot {
        val raw = assets.open("activity-samples.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        check(root.getString("schemaVersion") == "1.0.0")
        check(root.getString("mode") == "reviewed_sample")
        val sources = root.getJSONArray("sources").objects().associate { source ->
            val record = NoticeSource(source.getString("id"), source.opt("url") as? String,
                source.opt("kind") as? String, source.opt("checkedAt") as? String,
                source.opt("access") as? String, source.opt("note") as? String)
            record.id to record
        }
        val sourceURLs = sources.mapValues { it.value.url }
        val organizations = root.getJSONArray("organizations").objects().map {
            OrganizationModel(it.getString("id"), it.getString("name"),
                if (it.isNull("parentOrganizationId")) null else it.getString("parentOrganizationId"))
        }
        val feed = root.getJSONArray("activities").objects()
            .filter { it.getBoolean("demoVisible") }
            .map { item ->
                val evidence = decodeNoticeEvidence(item, sourceURLs)
                val sourceIds = item.getJSONArray("sourceIds").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }
                NoticeModel(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    aiDescription = item.getString("summary"),
                    organizationId = if (item.isNull("favoriteOrganizationId")) null else item.getString("favoriteOrganizationId"),
                    targetUser = item.getJSONObject("audience").getString("summary"),
                    participationCondition = item.getJSONObject("eligibility").getString("summary"),
                    applicationInformation = decodeNoticeApplication(item.getJSONObject("application")),
                    location = decodeNoticeLocation(item.getJSONObject("location")),
                    schedules = item.getJSONArray("schedule").objects().map(::decodeNoticePhase),
                    benefits = item.getJSONArray("benefits").objects().map { it.getString("summary") },
                    issues = item.getJSONArray("qualityIssues").objects().map { it.getString("summary") },
                    categoryPath = item.getJSONArray("categoryPath").let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    },
                    contexts = item.getJSONArray("contexts").objects().map {
                        NoticeContext(it.getString("organizationId"), it.getString("role"))
                    },
                    edition = if (item.isNull("edition")) null else item.getInt("edition"),
                    sourceURL = sourceURLs[sourceIds.firstOrNull()].orEmpty(),
                    organizationLinks = item.optJSONArray("organizationLinks")?.objects()?.map {
                        NoticeContext(it.getString("organizationId"), it.getString("role"))
                    }.orEmpty(),
                    sources = (sourceIds + evidence.map { it.sourceId }).distinct().map { id ->
                        sources[id] ?: NoticeSource(id, null, null, null, null, null)
                    },
                    evidence = evidence,
                )
            }.sortedBy { if (it.organizationId == null) 1 else 0 }
        return NoticeSnapshot(root.getString("snapshotAt").take(10), organizations, feed)
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
