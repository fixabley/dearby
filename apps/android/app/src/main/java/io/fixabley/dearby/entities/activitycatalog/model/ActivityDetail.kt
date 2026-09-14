package io.fixabley.dearby.entities.activitycatalog.model

/** Read-only transient projection. Only organizationId/role IDs are persisted in source records. */
internal data class ActivityDetail(
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
    val applicationInformation: ActivityApplication,
    val schedules: List<ActivityScheduleDetail>,
    val location: ActivityLocation,
    val benefits: List<String>,
    val issues: List<String>,
    val sourceURL: String,
    val sources: List<ActivitySource>,
    val evidence: List<ActivityEvidence>,
)

internal data class ResolvedOrganizationRole(val organizationId: String, val role: String, val label: String, val name: String?)
internal data class ActivityScheduleDetail(val period: ActivityPhase, val locations: List<ActivityVenue>)
