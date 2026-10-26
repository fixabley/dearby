package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.adaptor.web.v1.activities.ScheduleResponse
import io.wid.dearby.adaptor.web.v1.activities.SessionResponse
import io.wid.dearby.adaptor.web.v1.activities.TrackResponse
import io.wid.dearby.application.timetable.TimetableService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "트랙·세션 시간표": 세션 찜(로그인만). 활동 즐겨찾기·알림·추천 신호와 따로다. 같은 시간대 겹침은 앱이 계산한다
@RestController
@RequestMapping("/v1/me/session-favorites")
class SessionFavoriteController(private val timetables: TimetableService) {

    // 내 시간표: 시작 순. 게시되지 않았거나 지워진 일정의 세션은 뺀다
    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt): SessionFavoritesResponse =
        SessionFavoritesResponse(timetables.favorites(UUID.fromString(jwt.subject)).map {
            SessionFavoriteResponse(
                activityId = it.activity.id,
                activityName = it.activity.name,
                scheduleId = it.schedule.id,
                scheduleTitle = it.schedule.title,
                schedule = ScheduleResponse.from(it.schedule),
                track = it.track?.let { track -> TrackResponse(track.id, track.name, track.room, track.position) },
                session = SessionResponse.from(it.session, SessionResponse.zoneOf(it.schedule.time), favorited = true),
                favoritedAt = it.favoritedAt,
            )
        })

    @PutMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun put(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long) = timetables.putFavorite(UUID.fromString(jwt.subject), sessionId)

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long) = timetables.deleteFavorite(UUID.fromString(jwt.subject), sessionId)
}

data class SessionFavoriteResponse(
    val activityId: Long,
    val activityName: String,
    val scheduleId: Long,
    val scheduleTitle: String,
    val schedule: ScheduleResponse, // 부모 블록 전체(시작·끝·시간대). 앱이 활동을 다시 조회하지 않아도 된다(2026-10-26)
    val track: TrackResponse?, // 세션의 트랙. 전폭 세션(trackId null)이면 null
    val session: SessionResponse,
    val favoritedAt: OffsetDateTime,
)

data class SessionFavoritesResponse(val items: List<SessionFavoriteResponse>)
