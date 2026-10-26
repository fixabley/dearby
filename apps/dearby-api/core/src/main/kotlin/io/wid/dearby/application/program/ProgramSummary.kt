package io.wid.dearby.application.program

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.DiscoveryKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.Tag
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
    val openActivityCount: Int, // 모집 중 활동 수(모집 예정은 세지 않는다)
    val nearestRecruitmentEndAt: OffsetDateTime?, // 열린 활동 중 가장 이른 모집 마감. 모두 마감이 없으면 null
    // nearestRecruitmentEndAt이 모집 마감이 아니라 활동 시작(정렬 키의 대체값)인지. 앱은 이때 `마감` 대신 활동 날짜로 쓴다(native-v1.md "발견 카드 리뉴얼")
    val nearestIsActivityStart: Boolean = false,
    val matchedActivityIds: List<Id>, // q가 모집 중·모집 예정 활동 이름에 맞은 활동 id. q가 없으면 빈 목록
    val upcomingActivityCount: Int = 0, // 모집 예정 활동 수(native-v1.md "마감 숨기기", 2026-10-19 밤)
    val nearestRecruitmentStartAt: OffsetDateTime? = null, // 모집 예정 활동의 가장 이른 모집 시작(없으면 활동 시작). 모집 예정이 없으면 null
    val category: ActivityCategory? = null, // 프로그램 기본 종류(admin-v1.md "프로그램 기본 종류")
    val latestActivityAt: OffsetDateTime? = null, // 게시 활동의 가장 최근 활동 시작(native-v1.md "모집 중인 프로그램만 보기"). 없으면 null
    val tags: List<Tag> = emptyList(), // 프로그램 태그가 있으면 그것, 없으면 게시 활동 태그의 합집합(최대 6개, 사전 순)(native-v1.md "태그")
    val fieldTags: List<Tag> = emptyList(), // 분야 태그 최대 2개, 서버 순서(native-v1.md "분야·성격 태그와 아이콘")
    val traitTags: List<Tag> = emptyList(), // 성격 태그 전부, 사전 순
    val brandColor: String? = null, // 보일 로고(계승 포함)의 대표색 "#RRGGBB"(native-v1.md "맞춤 대기열"), 없으면 null
    val coverUrl: String? = null, // 대기열 커버 공개 경로 `/v1/programs/{id}/cover?v=…`(native-v1.md "맞춤 대기열"). 없거나 꺼졌으면 null
    val hasTimetable: Boolean = false, // 묶음의 모집 중·모집 예정 활동(kind 등 필터 반영) 중 하나라도 시간표 있는 일정이 있다(native-v1.md "트랙·세션 시간표")
    val logoUrl: String? = null, // 보일 로고 공개 경로: 프로그램 로고, 없으면 가장 가까운 조직 로고(native-v1.md "조직 로고와 로고 계승"), 없으면 null
)

// q는 프로그램 이름·영문 이름, 조직 이름, 열린 활동 이름에 부분 일치(대소문자 무시). cursor는 정렬된 목록의 위치(이전 nextCursor)
data class ProgramSummarySearch(
    val q: String?,
    val participationType: ParticipationType?,
    val cursor: Int,
    val size: Int,
    val eligibility: EligibilityFilter? = null, // 대상자 검색(native-v1.md "대상자 조건"). 있으면 열린 활동을 2차 판정까지 거쳐 세고 묶는다
    val categories: Set<ActivityCategory>? = null, // 활동 종류 필터(native-v1.md "활동 종류"). 열린 활동 수·맞은 id에 함께 적용
    val kind: DiscoveryKind? = null, // 프로그램·이벤트 전환(native-v1.md "발견 프로그램·이벤트 전환"). 활동 행을 걸러 묶음 수·마감·맞은 id 모두 그 kind 활동 기준. null이면 전부
    val openOnly: Boolean = true, // true면 모집 중·모집 예정 활동이 있는 프로그램(마감만 숨김), false면 최근 3년 안에 활동이 있었던 프로그램까지. 대상자 필터와 함께 쓰면 열린 것만
    val tagIds: Set<Id>? = null, // 태그 필터(native-v1.md "태그"): 모두 가진 활동만(AND). 열린 활동 수·맞은 id에 함께 적용
    val programIds: Set<Id>? = null, // 이 프로그램들만(즐겨찾기). 있으면 openOnly와 상관없이 게시 활동이 있는 프로그램을 모두 준다
)

// 대상자 필터가 있을 때 묶음을 만들 재료: 열린 활동 한 건(1차 판정을 지난 것)
data class OpenActivity(
    val id: Id,
    val programId: Id,
    val name: String,
    val nameEn: String?,
    val recruitmentEndAt: OffsetDateTime?, // 정렬 키: 모집 마감, 없으면 활동 시작
    val eligibility: EligibilityGroup?,
    val hasRecruitmentEnd: Boolean = true, // recruitmentEndAt이 실제 모집 마감인지(아니면 활동 시작)
    val hasTimetable: Boolean = false, // 시간표 있는 일정이 있다
)

// nextCursor가 null이면 마지막 페이지
data class ProgramSummaryPage(val items: List<ProgramSummary>, val nextCursor: Int?)
