package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminActivity
import io.wid.dearby.application.eligibility.EligibilityJson
import tools.jackson.databind.JsonNode
import io.wid.dearby.domain.PublicationStatus
import java.time.OffsetDateTime

// 공개 Activity 필드 + 운영자용 필드(admin-v1.md "활동")
data class AdminActivityResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val description: String,
    val descriptionEn: String?,
    val organization: ReferenceResponse,
    val program: ReferenceResponse,
    val officialUrl: String,
    val imageUrl: String?,
    val application: ScheduleResponse?,
    val activitySchedule: ScheduleResponse,
    val schedules: List<ScheduleResponse>,
    val publicationStatus: PublicationStatus,
    val officialSource: Boolean,
    val applicationUrl: String?,
    val location: String?,
    val cost: String?,
    val audience: String?,
    val eligibility: JsonNode?, // 대상자 조건(native-v1.md "대상자 조건"). 없으면 null
    val category: String?, // 활동 종류 코드(native-v1.md "활동 종류"). null은 미분류
    val inheritedFields: List<String>, // 이전 기수에서 가져온 필드 이름(admin-v1.md "이전 기수 정보 자동완성")
    val curatedFields: List<String>, // 어드민이 고친 필드 이름(admin-v1.md "운영자 지정 필드")
    val sourceCheckedAt: OffsetDateTime?,
) {
    companion object {
        fun from(item: AdminActivity): AdminActivityResponse {
            val activity = item.listing.activity
            val curation = item.curation
            return AdminActivityResponse(
                id = activity.id,
                name = activity.name,
                nameEn = activity.nameEn,
                description = activity.description,
                descriptionEn = activity.descriptionEn,
                organization = ReferenceResponse(item.listing.organization.id, item.listing.organization.name),
                program = ReferenceResponse(item.listing.program.id, item.listing.program.name),
                officialUrl = activity.officialUrl,
                imageUrl = activity.imageUrl,
                application = activity.application?.let(ScheduleResponse::from),
                activitySchedule = ScheduleResponse.from(activity.activitySchedule),
                schedules = activity.schedules.map(ScheduleResponse::from),
                publicationStatus = curation.publicationStatus,
                officialSource = curation.officialSource,
                applicationUrl = curation.applicationUrl,
                location = curation.location,
                cost = curation.cost,
                audience = curation.audience,
                eligibility = activity.eligibility?.let(EligibilityJson::toNode),
                category = activity.category?.name,
                inheritedFields = curation.inheritedFields,
                curatedFields = curation.curatedFields,
                sourceCheckedAt = curation.sourceCheckedAt,
            )
        }
    }
}
