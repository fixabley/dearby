package io.fixabley.dearby.entities.activitycatalog.model

internal data class Organization(val id: String, val name: String, val parentOrganizationId: String?)
internal data class NoticeContext(val organizationId: String, val role: String) {
    val label: String get() = when (role) {
        "venue_institution" -> "개최 기관"
        "audience_institution" -> "참여 대상 기관"
        "co_operator" -> "공동 운영"
        else -> "행사 관련 기관"
    }
}

internal data class Notice(
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

data internal class ActivityCatalog(
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
}
