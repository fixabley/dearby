package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.eligibility.EligibilityFilter
import io.wid.dearby.domain.eligibility.EligibilityGroup
import java.time.OffsetDateTime

// 발견 화면의 프로그램 묶음 한 항목(native-v1.md "발견 프로그램 묶음"). 열린 활동이 1개 이상인 프로그램만 만들어진다
data class ProgramSummary(
    val id: Id,
    val name: String,
    val nameEn: String?,
    val organizationId: Id,
    val organizationName: String,
    val organizationNameEn: String?,
    val openActivityCount: Int,
    val nearestRecruitmentEndAt: OffsetDateTime?, // 열린 활동 중 가장 이른 모집 마감. 모두 마감이 없으면 null
    val matchedActivityIds: List<Id>, // q가 열린 활동 이름에 맞은 활동 id. q가 없으면 빈 목록
)

// q는 프로그램 이름·영문 이름, 조직 이름, 열린 활동 이름에 부분 일치(대소문자 무시). cursor는 정렬된 목록의 위치(이전 nextCursor)
data class ProgramSummarySearch(
    val q: String?,
    val participationType: ParticipationType?,
    val cursor: Int,
    val size: Int,
    val eligibility: EligibilityFilter? = null, // 대상자 검색(native-v1.md "대상자 조건"). 있으면 열린 활동을 2차 판정까지 거쳐 세고 묶는다
)

// 대상자 필터가 있을 때 묶음을 만들 재료: 열린 활동 한 건(1차 판정을 지난 것)
data class OpenActivity(
    val id: Id,
    val programId: Id,
    val name: String,
    val nameEn: String?,
    val recruitmentEndAt: OffsetDateTime?,
    val eligibility: EligibilityGroup?,
)

// nextCursor가 null이면 마지막 페이지
data class ProgramSummaryPage(val items: List<ProgramSummary>, val nextCursor: Int?)
