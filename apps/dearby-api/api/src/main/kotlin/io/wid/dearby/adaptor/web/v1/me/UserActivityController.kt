package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.useractivity.UserActivityService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.useractivity.SchedulePick
import io.wid.dearby.domain.useractivity.UserActivity
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "내 활동 서버 저장·일정 상태": 로그인 사용자의 내 활동. 로그인하지 않았으면 앱이 기기 안에 둔다
@RestController
@RequestMapping("/v1/me/activities")
class UserActivityController(private val userActivities: UserActivityService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = UserActivityListResponse(userActivities.list(jwt.userId()).map(UserActivityResponse::from))

    @PutMapping("/{activityId}")
    // 본문은 없거나 비어 있다(참여 확정 표시는 없앴다, 2026-10-22)
    fun put(@AuthenticationPrincipal jwt: Jwt, @PathVariable activityId: Long) =
        UserActivityResponse.from(userActivities.put(jwt.userId(), activityId))

    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable activityId: Long) = userActivities.delete(jwt.userId(), activityId)

    @PutMapping("/{activityId}/schedules/{scheduleId}")
    fun pick(@AuthenticationPrincipal jwt: Jwt, @PathVariable activityId: Long, @PathVariable scheduleId: Long, @RequestBody request: SchedulePickRequest) =
        UserActivityResponse.from(userActivities.pick(jwt.userId(), activityId, scheduleId, request.statusId, request.kind?.let(::kind)))

    @PostMapping("/import")
    fun import(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UserActivityImportRequest) =
        UserActivityListResponse(
            userActivities.import(
                jwt.userId(),
                request.items.map { item ->
                    UserActivityService.ImportItem(
                        item.activityId ?: throw InvalidInputException("activityId가 필요합니다"),
                        item.statuses.map { (it.scheduleId ?: throw InvalidInputException("scheduleId가 필요합니다")) to kind(it.kind.orEmpty()) },
                    )
                },
            ).map(UserActivityResponse::from)
        )

    private fun Jwt.userId(): UUID = UUID.fromString(subject)

    private fun kind(value: String): ProgressKind =
        ProgressKind.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw InvalidInputException("kind는 WAITING·PASSED·FAILED·OTHER 중 하나입니다")
}

data class SchedulePickRequest(val statusId: Long? = null, val kind: String? = null)

data class UserActivityImportRequest(val items: List<ImportItemRequest> = emptyList())

data class ImportItemRequest(val activityId: Long? = null, val statuses: List<ImportStatusRequest> = emptyList())

data class ImportStatusRequest(val scheduleId: Long? = null, val kind: String? = null)

data class UserActivityListResponse(val items: List<UserActivityResponse>)

// deleted: 활동이 지워졌거나 게시가 내려가 목록에서 "삭제된 활동"으로 보인다
data class UserActivityResponse(
    val activityId: Long,
    val createdAt: OffsetDateTime,
    val deleted: Boolean,
    val statuses: List<SchedulePick>,
) {
    companion object {
        fun from(item: UserActivity) = UserActivityResponse(item.activityId, item.createdAt, item.deleted, item.statuses)
    }
}
