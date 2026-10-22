package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ScheduleEdit
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Address
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.eligibility.EligibilityGroup

// 컨트롤러가 해석한 활동 직접 등록 입력(admin-v1.md "POST /v1/admin/activities"). null은 보내지 않은 필드
data class ActivityCreateInput(
    val programId: Id,
    val name: String,
    val nameEn: String?,
    val description: String,
    val descriptionEn: String?,
    val officialUrl: String,
    val applicationUrl: String?,
    val participationType: ParticipationType?,
    val category: ActivityCategory?,
    val tagIds: List<Id>? = null, // 태그 id 목록(native-v1.md "태그"). null이면 프로그램 태그를 기본으로 쓴다
    val activitySchedule: ScheduleEdit?,
    val activityDateLabel: String?,
    val application: ScheduleEdit?,
    val eligibility: EligibilityGroup?,
    val audience: String?,
    val location: String?,
    val place: Address?, // 좌표가 있는 활동 장소(admin-v1.md "활동 장소와 지오코딩"). null이면 없음
    val cost: String?,
    val activityOverlap: OverlapChange? = null, // 활동·모집 기간 객체의 overlapCheck. null이면 보내지 않음
    val applicationOverlap: OverlapChange? = null,
)
