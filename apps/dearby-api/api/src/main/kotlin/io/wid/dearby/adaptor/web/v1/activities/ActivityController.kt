package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 인증 없이 받는다(SecurityConfig). 한 항목에 소속 기관·프로그램 요약이 있어 다른 조회를 기다리지 않고 화면을 그린다
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
    ): ActivityPageResponse {
        val page = activities.search(ActivitySearch(q, organizationId, programId, cursor, size))
        return ActivityPageResponse(page.items.map(ActivityResponse::from), page.nextCursor)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ActivityResponse = ActivityResponse.from(activities.getById(id))
}
