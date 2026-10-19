package io.wid.dearby.application.activity

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.curation.ActivityCuration
import io.wid.dearby.domain.eligibility.EligibilityGroup

// 운영자용 활동 정보 저장소 포트. 구현은 adaptor/persistence
interface ActivityCurationRepository {

    fun findAllByActivityId(activityIds: Collection<Id>): List<ActivityCuration>

    // 바뀐 행이 없으면(활동 없음) false
    fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean

    // 관리자가 고친 한글·영문 이름과 설명, 공식 안내·신청 링크(2026-10-19). touched는 요청에 실제로 있던 필드 이름으로, 운영자 지정 필드에 남긴다.
    // 바뀐 행이 없으면(활동 없음) false
    fun updateTexts(
        activityId: Id, name: String, nameEn: String?, description: String, descriptionEn: String?,
        officialUrl: String, applicationUrl: String?, touched: Set<String>,
    ): Boolean

    // 관리자가 고친 대상자 조건(null이면 지운다). 검색용 펼친 조건도 함께 다시 쓴다. 바뀐 행이 없으면(활동 없음) false
    fun updateEligibility(activityId: Id, eligibility: EligibilityGroup?): Boolean

    // 활동 종류(native-v1.md "활동 종류"): null이면 미분류. 운영자 지정 필드 category에 남긴다. 활동이 없으면 false
    fun updateCategory(activityId: Id, category: ActivityCategory?): Boolean

    // 운영자 직접 등록(admin-v1.md "POST /v1/admin/activities"): 초안 행과 활동 기간 일정(문구, curated)을 만들고 id를 돌려준다
    fun insert(creation: ActivityCreation): Id

    // 같은 프로그램에 공백만 다른 같은 이름이 있는지(프로그램 이름 규칙과 같다)
    fun existsName(programId: Id, name: String): Boolean

    // 운영자가 직접 지정한 활동 기간·모집 기간(admin-v1.md "일정 직접 지정"). time이 null이면 구조화된 값을 지우고 dateLabel만 남기며 curated를 푼다.
    // APPLICATION 일정이 없으면 만든다. 활동이 없으면 false
    fun updateSchedule(activityId: Id, slot: ScheduleSlot, time: ScheduleTime?): Boolean

    // 활동을 지운다(일정·역할·연락처는 FK로 함께). 수집으로 생긴 활동이면 그 출처를 다시 수집하지 않게 남긴다
    fun delete(activityId: Id): Boolean
}
