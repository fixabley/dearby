package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.shared.ui.compactPeriodText
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
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
    val applicationDraft: CalendarDraft?,
    val phaseDrafts: List<CalendarDraft?>,
    val saved: Boolean = false,
) {
    val applicationDateText: String get() = applicationInformation.let {
        compactPeriodText(it.opensAt, it.opensOn, it.closesAt, it.closesOn, it.timezone, it.summary, "마감")
    }
    val applicationDateDescription: String get() = applicationInformation.let {
        listOfNotNull("신청 기간: $applicationDateText", it.summary, "시간대: ${it.timezone}",
            it.opensAt?.let { value -> "시작 시각: $value" }, it.opensOn?.let { value -> "시작 날짜: $value" },
            it.closesAt?.let { value -> "마감 시각: $value" }, it.closesOn?.let { value -> "마감 날짜: $value" }).joinToString(". ")
    }
    val applicationPlaceText: String get() = applicationInformation.let {
        val channels = it.methods.map { method -> when (method) {
            "platform" -> "온라인 신청"; "email" -> "이메일"; "office" -> "방문 접수"; "postal" -> "우편"; else -> method
        } }
        (channels + it.submissionLocations).joinToString(" · ").ifBlank { "신청 방법·장소 미확인" }
    }
}

internal data class ResolvedOrganizationRole(val organizationId: String, val role: String, val label: String, val name: String?)
internal data class NoticeScheduleState(val period: NoticePhase, val locations: List<NoticeVenue>) {
    val title: String get() = if (period.mode == "online" && !period.label.startsWith("온라인")) "온라인 ${period.label}" else period.label
    val dateText: String get() = compactPeriodText(period.startsAt, period.startsOn, period.endsAt, period.endsOn,
        period.timezone, period.summary)
    val dateDescription: String get() = listOfNotNull("$title, $dateText", period.summary, "시간대: ${period.timezone}",
        period.startsAt?.let { "시작 시각: $it" }, period.startsOn?.let { "시작 날짜: $it" },
        period.endsAt?.let { "종료 시각: $it" }, period.endsOn?.let { "종료 날짜: $it" },
        "종료".takeIf { period.endsAt == null && period.endsOn == null }?.let { "$it 미확인" }).joinToString(". ")
    val placeText: String get() = if (period.mode == "online") "온라인" else
        locations.joinToString(" / ") { it.displayName }.ifBlank { "장소 미확인" }
    val placeDescription: String get() = if (period.mode == "online") {
        listOfNotNull("온라인", period.onlineUrl).joinToString(" · ")
    } else locations.joinToString(" / ") { venue ->
        listOfNotNull(venue.name, venue.address).distinct().joinToString(" · ").ifBlank { "장소 미확인" }
    }.ifBlank { "장소 미확인" }
}
