package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.discovery.ProgramSummaryResponse
import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.application.notification.NotificationService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "알림 목록(발견 우상단 종)": 프로그램 카드로 묶은 알림(가장 최근 알림 순, 한 번에 20묶음)과 읽음 처리. 로그인만
@RestController
@RequestMapping("/v1/me/notifications")
class NotificationController(private val notifications: NotificationService, private val favorites: FavoriteService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") cursor: Int,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<NotificationPageResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val userId = UUID.fromString(jwt.subject)
        val page = notifications.page(userId, cursor)
        val mine = favorites.ids(userId)
        val groups = page.groups.map { group ->
            NotificationGroupResponse(
                ProgramSummaryResponse.from(group.program, language, mine), group.unread,
                group.items.map { NotificationItemResponse(it.id, it.kind.name, NotificationActivityResponse(it.activityId, it.activityName), it.createdAt, it.readAt) },
            )
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, language.tag).body(NotificationPageResponse(groups, page.nextCursor))
    }

    @GetMapping("/unread-count")
    fun unreadCount(@AuthenticationPrincipal jwt: Jwt) = UnreadCountResponse(notifications.unreadCount(UUID.fromString(jwt.subject)))

    // 행을 누르거나 푸시(notificationId)로 열면 읽음. 이미 읽었으면 그대로, 내 알림이 아니면 404
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = notifications.read(UUID.fromString(jwt.subject), id)

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll(@AuthenticationPrincipal jwt: Jwt) = notifications.readAll(UUID.fromString(jwt.subject))
}

data class NotificationActivityResponse(val id: Long, val name: String)

// kind: NEW·APPLICATION_START·DEADLINE_EVE·DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT·ACTIVITY_START. readAt이 null이면 읽지 않음
data class NotificationItemResponse(val id: Long, val kind: String, val activity: NotificationActivityResponse, val createdAt: OffsetDateTime, val readAt: OffsetDateTime?)

// unread: 그 프로그램의 읽지 않은 수. items는 최근 순
data class NotificationGroupResponse(val program: ProgramSummaryResponse, val unread: Int, val items: List<NotificationItemResponse>)

// nextCursor를 다음 요청의 cursor로. null이면 마지막
data class NotificationPageResponse(val groups: List<NotificationGroupResponse>, val nextCursor: Int?)

data class UnreadCountResponse(val count: Int)
