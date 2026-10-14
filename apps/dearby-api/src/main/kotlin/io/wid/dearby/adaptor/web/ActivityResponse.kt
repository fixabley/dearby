package io.wid.dearby.adaptor.web

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.domain.Freshness
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.RecruitmentStatus
import java.time.OffsetDateTime

// recruitmentStatus·freshness·isRecruiting은 응답 시각 기준으로 계산한 값
data class ActivityResponse(
    val id: Long,
    val name: String,
    val description: String,
    val organization: ReferenceResponse,
    val program: ReferenceResponse,
    val participationType: ParticipationType,
    val recruitmentStatus: RecruitmentStatus,
    val freshness: Freshness,
    val isRecruiting: Boolean,
    val location: String?,
    val cost: String?,
    val audience: String?,
    val roles: List<String>,
    val officialUrl: String,
    val applicationUrl: String?,
    val sourceCheckedAt: OffsetDateTime?,
    val validUntil: OffsetDateTime?,
    val sourceNote: String,
    val application: ScheduleResponse?, // 모집 기간
    val activitySchedule: ScheduleResponse, // 활동 기간·날짜 문구
    val schedules: List<ScheduleResponse>,
) {
    companion object {
        fun from(listing: ActivityListing): ActivityResponse {
            val activity = listing.activity
            return ActivityResponse(
                id = activity.id,
                name = activity.name,
                description = activity.description,
                organization = ReferenceResponse(listing.organization.id, listing.organization.name),
                program = ReferenceResponse(listing.program.id, listing.program.name),
                participationType = activity.participationType,
                recruitmentStatus = listing.recruitment.status,
                freshness = listing.recruitment.freshness,
                isRecruiting = listing.recruitment.isRecruiting,
                location = activity.location,
                cost = activity.cost,
                audience = activity.audience,
                roles = activity.roles,
                officialUrl = activity.officialUrl,
                applicationUrl = activity.applicationUrl,
                sourceCheckedAt = activity.sourceCheckedAt,
                validUntil = activity.validUntil,
                sourceNote = activity.sourceNote,
                application = activity.application?.let(ScheduleResponse::from),
                activitySchedule = ScheduleResponse.from(activity.activitySchedule),
                schedules = activity.schedules.map(ScheduleResponse::from),
            )
        }
    }
}
