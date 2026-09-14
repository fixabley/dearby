package io.fixabley.dearby.discovery

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Organization(val id: String, val name: String, val parentOrganizationId: String?)
data class NoticeContext(val organizationId: String, val role: String) {
    val label: String get() = when (role) {
        "venue_institution" -> "개최 기관"
        "audience_institution" -> "참여 대상 기관"
        "co_operator" -> "공동 운영"
        else -> "행사 관련 기관"
    }
}

data class Notice(
    val id: String,
    val title: String,
    val summary: String,
    val organizationId: String?,
    val audience: String,
    val eligibility: String,
    val application: String,
    val location: String,
    val schedule: List<String>,
    val benefits: List<String>,
    val issues: List<String>,
    val sourceUrl: String,
    val categoryPath: List<String>,
    val contexts: List<NoticeContext>,
    val edition: Int?,
) {
    val categorySummary: String get() {
        val labels = mapOf("recruitment" to "채용", "recruitment_event" to "채용행사",
            "competition" to "대회", "career" to "진로", "mentoring" to "멘토링",
            "academic_administration" to "학사 행정")
        return categoryPath.joinToString(" › ") { labels[it] ?: it }
    }
}

data class ActivityCatalog(
    val snapshotDate: String,
    val organizations: List<Organization>,
    val feed: List<Notice>,
) {
    fun organization(id: String?) = organizations.firstOrNull { it.id == id }

    fun organizationPath(id: String?): List<Organization> {
        val path = mutableListOf<Organization>()
        val seen = mutableSetOf<String>()
        var current = organization(id)
        while (current != null && seen.add(current.id)) {
            path.add(0, current)
            current = organization(current.parentOrganizationId)
        }
        return path
    }

    fun contextNames(notice: Notice): String = notice.contexts.distinctBy { it.organizationId }
        .mapNotNull { organization(it.organizationId)?.name }.joinToString(" · ")

    companion object {
        fun load(context: Context): ActivityCatalog {
            val raw = context.assets.open("activity-samples.json").bufferedReader().use { it.readText() }
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
                        location = item.getJSONObject("location").getString("summary"),
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
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

class FavoriteOrganizations(context: Context) {
    private val preferences = context.getSharedPreferences("dearby.favorites.v1", Context.MODE_PRIVATE)
    var ids: Set<String> by mutableStateOf(preferences.getStringSet("organizationIDs", emptySet())!!.toSet())
        private set

    fun save(id: String) {
        ids = ids + id
        persist()
    }

    fun remove(id: String) {
        ids = ids - id
        persist()
    }

    private fun persist() {
        preferences.edit().putStringSet("organizationIDs", ids).apply()
    }
}
