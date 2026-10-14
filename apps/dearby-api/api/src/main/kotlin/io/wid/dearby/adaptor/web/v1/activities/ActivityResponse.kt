package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.application.activity.ActivityListing

// 모집 여부는 application(모집 기간)으로 판단한다
data class ActivityResponse(
    val id: Long,
    val name: String,
    val description: String,
    val organization: ReferenceResponse,
    val program: ReferenceResponse,
    val officialUrl: String,
    val imageUrl: String?,
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
                officialUrl = activity.officialUrl,
                imageUrl = activity.imageUrl,
                application = activity.application?.let(ScheduleResponse::from),
                activitySchedule = ScheduleResponse.from(activity.activitySchedule),
                schedules = activity.schedules.map(ScheduleResponse::from),
            )
        }
    }
}
