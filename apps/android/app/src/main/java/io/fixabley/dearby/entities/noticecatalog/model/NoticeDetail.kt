package io.fixabley.dearby.entities.noticecatalog.model

/** Read-only transient projection. Only organizationId/role IDs are persisted in source records. */
internal data class NoticeDetail(
    val id: String,
    val title: String,
    val aiDescription: String,
    val descriptionProvenance: String,
    val organizationId: String?,
    val organizationPath: List<Organization>,
    val relatedOrganizations: List<ResolvedOrganizationRole>,
    val categoryPath: List<String>,
    val categorySummary: String,
    val contexts: List<ResolvedOrganizationRole>,
    val edition: Int?,
    val targetUser: String,
    val participationCondition: String,
    val applicationInformation: NoticeApplication,
    val schedules: List<NoticeScheduleDetail>,
    val location: NoticeLocation,
    val benefits: List<String>,
    val issues: List<String>,
    val sourceURL: String,
    val sources: List<NoticeSource>,
    val evidence: List<NoticeEvidence>,
)

internal data class ResolvedOrganizationRole(val organizationId: String, val role: String, val label: String, val name: String?)
internal data class NoticeScheduleDetail(val period: NoticePhase, val locations: List<NoticeVenue>)
