package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType
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
)

// nextCursor가 null이면 마지막 페이지
data class ProgramSummaryPage(val items: List<ProgramSummary>, val nextCursor: Int?)
