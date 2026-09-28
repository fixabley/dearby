package com.dearby.nativeapp.entities.catalog.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class OrganizationModel(val id: String, val name: String, val description: String)
@Serializable data class ProgramModel(val id: String, val organizationId: String, val title: String, val description: String)
@Serializable data class ScheduleModel(val id: String, val title: String, val startAt: String?, val endAt: String?, val dateLabel: String, val timeZone: String)
@Serializable data class ActivityModel(
    val id: String, val programId: String, val organizationId: String,
    val title: String, val summary: String, val participationType: String,
    val recruitmentStatus: String, val isRecruiting: Boolean,
    val recruitmentStartAt: String?, val recruitmentEndAt: String?,
    val dateLabel: String, val location: String?, val cost: String?,
    val audience: String?, val qualification: String?, val roles: List<String>,
    val schedules: List<ScheduleModel>, val officialUrl: String, val applicationUrl: String?,
    val sourceCheckedAt: String?, val validUntil: String?, val freshness: String, val sourceNote: String,
) {
    fun nextChange(now: Instant): Instant? = listOfNotNull(sourceCheckedAt.instant(), validUntil.instant(), sourceCheckedAt.instant()?.plusSeconds(86_400), recruitmentStartAt.instant(), recruitmentEndAt.instant()).filter { it.isAfter(now) }.minOrNull()
    fun fresh(now: Instant): Boolean {
        val checked = sourceCheckedAt.instant() ?: return false
        val until = validUntil.instant() ?: return false
        return freshness == "verified" &&
            !now.isBefore(checked) && now.isBefore(until) && now.isBefore(checked.plusSeconds(86_400))
    }
    fun closed(now: Instant): Boolean = recruitmentStatus == "closed" || recruitmentEndAt.instant()?.let { !now.isBefore(it) } == true
    fun current(now: Instant): Boolean = isRecruiting && recruitmentStatus == "open" && fresh(now) &&
            (recruitmentStartAt == null || recruitmentStartAt.instant()?.let { !now.isBefore(it) } == true) &&
            (recruitmentEndAt == null || recruitmentEndAt.instant()?.let { now.isBefore(it) } == true)
}
private fun String?.instant(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
@Serializable data class CatalogModel(val generatedAt: String, val organizations: List<OrganizationModel>, val programs: List<ProgramModel>, val activities: List<ActivityModel>) {
    fun validated(): CatalogModel {
        require(organizations.map { it.id }.toSet().size == organizations.size && organizations.all { it.id.isNotBlank() })
        require(programs.map { it.id }.toSet().size == programs.size && programs.all { it.id.isNotBlank() })
        require(activities.map { it.id }.toSet().size == activities.size && activities.all { it.id.isNotBlank() })
        val organizationIds = organizations.map { it.id }.toSet()
        val programsById = programs.associateBy { it.id }
        require(programs.all { it.organizationId in organizationIds })
        require(activities.all { it.organizationId in organizationIds && programsById[it.programId]?.organizationId == it.organizationId })
        return this
    }
}
@Serializable data class CatalogLocalModel(
    val programs: Set<String> = emptySet(), val organizations: Set<String> = emptySet(),
    val reports: Map<String, String> = emptyMap(),
)
