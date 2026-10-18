package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.application.program.ProgramSummary
import java.time.OffsetDateTime

// 발견 화면의 프로그램 묶음 머리글(native-v1.md "발견 프로그램 묶음"). 이름은 요청 언어 값 하나(영문이 없으면 한글)
data class ProgramSummaryResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val organization: ReferenceResponse,
    val openActivityCount: Int,
    val nearestRecruitmentEndAt: OffsetDateTime?,
    val matchedActivityIds: List<Long>,
) {
    companion object {
        fun from(summary: ProgramSummary, language: ResponseLanguage) = ProgramSummaryResponse(
            id = summary.id,
            name = language.pick(summary.name, summary.nameEn),
            nameEn = summary.nameEn,
            organization = ReferenceResponse(summary.organizationId, language.pick(summary.organizationName, summary.organizationNameEn)),
            openActivityCount = summary.openActivityCount,
            nearestRecruitmentEndAt = summary.nearestRecruitmentEndAt,
            matchedActivityIds = summary.matchedActivityIds,
        )
    }
}

// nextCursor를 다음 요청의 cursor로 보낸다. null이면 마지막 페이지
data class ProgramSummaryPageResponse(val items: List<ProgramSummaryResponse>, val nextCursor: Int?)
