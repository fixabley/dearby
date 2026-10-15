package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 인증 없이 받는다(SecurityConfig). 한 항목에 소속 기관·프로그램 요약이 있어 다른 조회를 기다리지 않고 화면을 그린다.
// 언어는 Accept-Language로 고르고, 캐시가 언어별로 나뉘도록 Vary를 붙인다
@RestController
@RequestMapping("/v1/activities")
class ActivityController(private val activities: ActivityService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) programId: Long?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = ActivityService.DEFAULT_PAGE_SIZE.toString()) size: Int,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<ActivityPageResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val page = activities.search(ActivitySearch(q, organizationId, programId, cursor, size))
        return localized(language, ActivityPageResponse(page.items.map { ActivityResponse.from(it, language) }, page.nextCursor))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<ActivityResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        return localized(language, ActivityResponse.from(activities.getById(id), language))
    }

    private fun <T : Any> localized(language: ResponseLanguage, body: T): ResponseEntity<T> =
        ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE).header(HttpHeaders.CONTENT_LANGUAGE, language.tag).body(body)
}
