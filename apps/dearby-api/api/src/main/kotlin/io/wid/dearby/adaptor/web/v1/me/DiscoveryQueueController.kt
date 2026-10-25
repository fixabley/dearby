package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.adaptor.web.v1.activities.ActivityResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.discovery.ProgramSummaryResponse
import io.wid.dearby.adaptor.web.v1.discovery.SceneResponse
import io.wid.dearby.application.discovery.DiscoveryQueueService
import io.wid.dearby.application.discovery.QueuePage
import io.wid.dearby.application.discovery.StoryScenes
import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.domain.favorite.FavoriteIds
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

// native-v1.md "맞춤 대기열(스토리형 쇼츠)": 로그인 사용자의 오늘 대기열과 반응 신호. 비로그인은 추천 API와 기기 저장을 쓴다
@RestController
@RequestMapping("/v1/me")
class DiscoveryQueueController(private val queue: DiscoveryQueueService, private val favorites: FavoriteService) {

    @GetMapping("/discovery-queue")
    fun get(@AuthenticationPrincipal jwt: Jwt, @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?) =
        respond(queue.queue(UUID.fromString(jwt.subject)), favorites.ids(UUID.fromString(jwt.subject)), acceptLanguage)

    // 더 받기: 남은 후보로 10개를 뒤에 붙인 오늘 대기열 전체
    @PostMapping("/discovery-queue/refill")
    fun refill(@AuthenticationPrincipal jwt: Jwt, @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?) =
        respond(queue.refill(UUID.fromString(jwt.subject)), favorites.ids(UUID.fromString(jwt.subject)), acceptLanguage)

    @PostMapping("/recommendation-signals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signal(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: SignalRequest) =
        queue.signal(UUID.fromString(jwt.subject), request.activityId, request.kind, request.tagId)

    // 되돌리기: 그 신호를 지운다(없어도 204)
    @DeleteMapping("/recommendation-signals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undo(@AuthenticationPrincipal jwt: Jwt, @RequestParam activityId: Long, @RequestParam kind: String) =
        queue.undo(UUID.fromString(jwt.subject), activityId, kind)

    private fun respond(page: QueuePage, mine: FavoriteIds, acceptLanguage: String?): ResponseEntity<DiscoveryQueueResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val items = page.items.map {
            val activity = ActivityResponse.from(it.listing, language, mine)
            DiscoveryQueueItemResponse(
                activity, ProgramSummaryResponse.from(it.program, language, mine), it.reasons,
                StoryScenes.of(it.listing, it.reasons).map { s -> SceneResponse.from(s, activity.name) },
                mySignal = if (it.liked) "LIKE" else null,
            )
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, language.tag).body(DiscoveryQueueResponse(page.date, items, page.seen))
    }
}

data class SignalRequest(val activityId: Long, val kind: String, val tagId: Long? = null)

// mySignal: 지금 관심 있어요 상태("LIKE" 또는 null)
data class DiscoveryQueueItemResponse(
    val activity: ActivityResponse,
    val program: ProgramSummaryResponse,
    val reasons: List<String>,
    val scenes: List<Map<String, Any?>>,
    val mySignal: String?,
)

// date: 서울 날짜, seen: 오늘 반응한 카드 수(앱은 처음 반응하지 않은 카드부터)
data class DiscoveryQueueResponse(val date: LocalDate, val items: List<DiscoveryQueueItemResponse>, val seen: Int)
