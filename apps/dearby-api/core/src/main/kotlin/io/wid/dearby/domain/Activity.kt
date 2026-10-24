package io.wid.dearby.domain

import io.wid.dearby.domain.eligibility.EligibilityGroup

data class Activity(
    val id: Id,
    val programId: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름(없으면 null)
    val description: String, // 한글 설명. ICS 내보낼 때 각 일정의 DESCRIPTION
    val descriptionEn: String?, // 영문 설명(없으면 null)
    val contacts: List<ActivityContact>,
    val officialUrl: String, // 공식 안내 원문 링크
    val applicationUrl: String? = null, // 신청 링크(activities.application_url, native-v1.md "신청 확인 조건 완화·공개 applicationUrl"). 없으면 null
    val imageUrl: String?, // 대표 이미지. 없으면 null
    val application: Schedule?, // 신청 기간(kind = DEADLINE). 시작·끝 중 아는 쪽만 있을 수 있다. null: 상시 모집 등 신청 기간 없음
    val activitySchedule: Schedule, // 활동 기간(kind = EVENT, 종일 일정). 활동 장소의 기준
    val schedules: List<Schedule>, // 면접·발표 등 개별 일정
    val eligibility: EligibilityGroup?, // 대상자 조건(native-v1.md "대상자 조건"). 없으면 null(원문 audience만)
    val timestamps: Timestamps,
    val category: ActivityCategory? = null, // 활동 종류(native-v1.md "활동 종류"). null은 미분류
    val tags: List<Tag> = emptyList(), // 태그(native-v1.md "태그"), 사전 순
    val fieldTags: List<Tag> = emptyList(), // 분야 태그 전부(native-v1.md "분야·성격 태그와 아이콘"): 연관도 순, 없으면 프로그램 분야 순서
    val eligibilityChecked: Boolean = false, // 지원 자격을 확인했는지(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"): 조건 없음이 "제한 없음"인지 "아직 모름"인지 나눈다
    val location: String? = null, // 장소 문구(activities.location). 좌표가 있는 장소는 activitySchedule.address(native-v1.md "활동 장소")
    val hook: String? = null, // 대기열 장면 ① 한 줄 소개(native-v1.md "맞춤 대기열"). 없으면 null
    val highlights: List<Highlight> = emptyList(), // 대기열 장면 ② 핵심 줄(최대 3개)
) {
    // 사용자가 캘린더에 추가할 일정을 고르는 후보
    val calendarCandidates: List<Schedule> get() = listOfNotNull(application, activitySchedule) + schedules
}
