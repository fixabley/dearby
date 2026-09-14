package io.fixabley.dearby.entities.notice.model

internal data class NoticeContext(val organizationId: String, val role: String) {
    val label: String get() = when (role) {
        "venue_institution" -> "개최 기관"
        "audience_institution" -> "참여 대상 기관"
        "co_operator" -> "공동 운영"
        else -> "행사 관련 기관"
    }
}

internal data class NoticeModel(
    val id: String,
    val title: String,
    val aiDescription: String,
    val organizationId: String?,
    val targetUser: String,
    val participationCondition: String,
    val applicationInformation: NoticeApplication,
    val location: NoticeLocation,
    val schedules: List<NoticePhase>,
    val benefits: List<String>,
    val issues: List<String>,
    val sourceURL: String,
    val categoryPath: List<String>,
    val contexts: List<NoticeContext>,
    val edition: Int?,
    val organizationLinks: List<NoticeContext> = emptyList(),
    val sources: List<NoticeSource> = emptyList(),
    val evidence: List<NoticeEvidence> = emptyList(),
    val descriptionProvenance: String = "reviewed_sample_summary",
) {
    fun venuesFor(phase: NoticePhase): List<NoticeVenue> =
        if (phase.mode == "online") emptyList() else location.venues.filter { it.phase == phase.phase }

    val categorySummary: String get() {
        val labels = mapOf("recruitment" to "채용", "recruitment_event" to "채용행사",
            "competition" to "대회", "career" to "진로", "mentoring" to "멘토링",
            "academic_administration" to "학사 행정")
        return categoryPath.joinToString(" › ") { labels[it] ?: it }
    }
}
