package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.entities.notice.model.NoticeApplication
import io.fixabley.dearby.entities.notice.model.NoticeLocation
import io.fixabley.dearby.entities.notice.model.NoticeSource
import io.fixabley.dearby.entities.notice.model.NoticeEvidence
import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.entities.notice.model.NoticeVenue

/** Read-only transient projection. Only organizationId/role IDs are persisted in source records. */
internal data class NoticeDetailState(
    val id: String,
    val title: String,
    val aiDescription: String,
    val descriptionProvenance: String,
    val organizationId: String?,
    val organizationName: String?,
    val ancestorNames: List<String>,
    val relatedOrganizations: List<ResolvedOrganizationRole>,
    val categoryPath: List<String>,
    val categorySummary: String,
    val contexts: List<ResolvedOrganizationRole>,
    val edition: Int?,
    val targetUser: String,
    val participationCondition: String,
    val applicationInformation: NoticeApplication,
    val schedules: List<NoticeScheduleState>,
    val location: NoticeLocation,
    val benefits: List<String>,
    val issues: List<String>,
    val sourceURL: String,
    val sources: List<NoticeSource>,
    val evidence: List<NoticeEvidence>,
    val saved: Boolean = false,
)

internal data class ResolvedOrganizationRole(val organizationId: String, val role: String, val label: String, val name: String?)
internal data class NoticeScheduleState(val period: NoticePhase, val locations: List<NoticeVenue>)
