package io.wid.dearby.domain.collection

import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.eligibility.EligibilityGroup

import java.time.LocalDate
import java.time.OffsetDateTime

// 수집이 공식 페이지에서 확인한 활동 한 건. 초안(DRAFT)으로 저장되고 관리자가 게시해야 공개된다
data class CollectedActivity(
    val roundKey: String, // 같은 페이지의 회차·활동을 구분하는 식별자(예: "2026-본행사"). 다시 수집할 때 같은 행을 찾는 키의 일부
    val name: String,
    val nameEn: String?, // 수집 때 모델이 번역한 영문 이름(검토 대상)
    val description: String,
    val descriptionEn: String?, // 수집 때 모델이 번역한 영문 설명(검토 대상)
    val officialUrl: String, // 원문을 확인한 페이지
    val officialSource: Boolean, // 그 페이지가 프로그램의 공식 도메인(허용 호스트)인지. 웹 전체 검색이면 false
    val applicationUrl: String?, // 원문 페이지의 링크로 확인된 신청 주소만
    val participationType: ParticipationType,
    val recruitmentStartAt: OffsetDateTime?,
    val recruitmentEndAt: OffsetDateTime?,
    val recruitmentStartDate: LocalDate? = null, // 시각 없이 날짜만 있는 모집 기간(종일 일정으로 저장)
    val recruitmentEndDate: LocalDate? = null,
    val dateLabel: String, // 활동 기간 문구
    val startDate: LocalDate? = null, // 날짜만 있는 활동 기간(종일 일정). 없으면 dateLabel만
    val endDate: LocalDate? = null,
    val schedules: List<CollectedSchedule>,
    val location: String?,
    val cost: String?,
    val audience: String?,
    val eligibility: EligibilityGroup?, // 대상자 조건(native-v1.md "대상자 조건"). 모델이 뽑은 것을 어휘로 정리한 결과
    val evidenceQuote: String, // 원문에 그대로 있는 구절(확인함)
    val checkedAt: OffsetDateTime,
)
