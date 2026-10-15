package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.application.activity.ActivityListing

// 모집 여부는 application(모집 기간)으로 판단한다. 이름·설명은 요청 언어 값 하나(영문이 없으면 한글)
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
        fun from(listing: ActivityListing, language: ResponseLanguage): ActivityResponse {
            val activity = listing.activity
            return ActivityResponse(
                id = activity.id,
                name = language.pick(activity.name, activity.nameEn),
                description = language.pick(activity.description, activity.descriptionEn),
                organization = ReferenceResponse(listing.organization.id, language.pick(listing.organization.name, listing.organization.nameEn)),
                program = ReferenceResponse(listing.program.id, language.pick(listing.program.name, listing.program.nameEn)),
                officialUrl = activity.officialUrl,
                imageUrl = activity.imageUrl,
                application = activity.application?.let(ScheduleResponse::from),
                activitySchedule = ScheduleResponse.from(activity.activitySchedule),
                schedules = activity.schedules.map(ScheduleResponse::from),
            )
        }
    }
}
