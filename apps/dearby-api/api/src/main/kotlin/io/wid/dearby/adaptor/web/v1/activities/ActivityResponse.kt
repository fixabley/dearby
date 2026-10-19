package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.adaptor.web.v1.tags.TagResponse
import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.eligibility.EligibilityJson
import tools.jackson.databind.JsonNode

// 모집 여부는 application(모집 기간)으로 판단한다. 이름·설명은 요청 언어 값 하나(영문이 없으면 한글)
data class ActivityResponse(
    val id: Long,
    val name: String,
    val description: String,
    val organization: ReferenceResponse,
    val program: ReferenceResponse,
    val officialUrl: String,
    val applicationUrl: String?, // 신청 링크(native-v1.md "신청 확인 조건 완화·공개 applicationUrl"). 없으면 null. CTA는 applicationUrl ?? officialUrl
    val imageUrl: String?,
    val application: ScheduleResponse?, // 모집 기간
    val activitySchedule: ScheduleResponse, // 활동 기간·날짜 문구
    val schedules: List<ScheduleResponse>,
    val eligibility: JsonNode?, // 대상자 조건(native-v1.md "대상자 조건"). 없으면 null
    val category: String?, // 활동 종류 코드(native-v1.md "활동 종류"). null은 미분류
    val tags: List<TagResponse>, // 태그(native-v1.md "태그"), 사전 순
    val location: String?, // 장소 문구(native-v1.md "활동 장소"). 좌표 장소가 있으면 그 이름, 없으면 수집·운영자 문구
    val place: PlaceResponse?, // 좌표가 있는 활동 장소. 없으면 null
) {
    companion object {
        fun from(listing: ActivityListing, language: ResponseLanguage): ActivityResponse {
            val activity = listing.activity
            val place = activity.activitySchedule.address?.let(PlaceResponse::from)
            return ActivityResponse(
                id = activity.id,
                name = language.pick(activity.name, activity.nameEn),
                description = language.pick(activity.description, activity.descriptionEn),
                organization = ReferenceResponse(listing.organization.id, language.pick(listing.organization.name, listing.organization.nameEn)),
                program = ReferenceResponse(listing.program.id, language.pick(listing.program.name, listing.program.nameEn)),
                officialUrl = activity.officialUrl,
                applicationUrl = activity.applicationUrl,
                imageUrl = activity.imageUrl,
                application = activity.application?.let(ScheduleResponse::from),
                activitySchedule = ScheduleResponse.from(activity.activitySchedule),
                schedules = activity.schedules.map(ScheduleResponse::from),
                eligibility = activity.eligibility?.let(EligibilityJson::toNode),
                category = activity.category?.name,
                tags = activity.tags.map(TagResponse::from),
                location = place?.name ?: activity.location,
                place = place,
            )
        }
    }
}
