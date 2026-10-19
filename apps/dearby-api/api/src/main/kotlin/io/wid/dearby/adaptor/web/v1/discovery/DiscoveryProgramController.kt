package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.application.program.ProgramSummarySearch
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.eligibility.EligibilityFilter
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 발견 화면의 프로그램 묶음(native-v1.md "발견 프로그램 묶음"). 인증 없이 받는다(ApiAccessRules).
// 경로는 /v1/discovery/programs: /v1/programs는 로그인한 이력 선택 창의 프로그램 검색(activityinfo)이 이미 쓴다
// 열린 활동이 있는 프로그램만, 가장 이른 모집 마감 순. 펼칠 때는 GET /v1/activities?programId= 를 쓴다
@RestController
@RequestMapping("/v1/discovery/programs")
class DiscoveryProgramController(private val summaries: ProgramSummaryService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) participationType: String?,
        @RequestParam(required = false) category: String?, // 활동 종류 코드 하나 또는 쉼표 목록(native-v1.md "활동 종류")
        @RequestParam(defaultValue = "true") openOnly: Boolean, // false면 최근 2년 안에 활동이 있었던 프로그램까지(native-v1.md "모집 중인 프로그램만 보기")
        @RequestParam(defaultValue = "0") cursor: Int,
        @RequestParam(defaultValue = ProgramSummaryService.DEFAULT_PAGE_SIZE.toString()) size: Int,
        @RequestParam params: Map<String, String>, // 대상자 필터(age·educationStatus 등 camelCase 어휘)
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<ProgramSummaryPageResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val type = participationType?.let { value ->
            ParticipationType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw InvalidInputException("participationType은 REGISTRATION 또는 SELECTION입니다")
        }
        val page = summaries.search(ProgramSummarySearch(q, type, cursor, size, EligibilityFilter.from(params), ActivityCategory.parseList(category), openOnly = openOnly))
        return ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE).header(HttpHeaders.CONTENT_LANGUAGE, language.tag)
            .body(ProgramSummaryPageResponse(page.items.map { ProgramSummaryResponse.from(it, language) }, page.nextCursor))
    }
}
