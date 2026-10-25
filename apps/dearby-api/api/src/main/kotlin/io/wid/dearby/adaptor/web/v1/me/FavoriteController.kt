package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.adaptor.web.v1.activities.ActivityResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.discovery.ProgramSummaryResponse
import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "즐겨찾기(하트)·새 모집 푸시 알림": 활동·프로그램 하트(로그인만). 활동 하트는 맞춤 대기열 LIKE를 함께 남기고 지운다
@RestController
@RequestMapping("/v1/me/favorites")
class FavoriteController(private val favorites: FavoriteService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt, @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?): ResponseEntity<FavoritesResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val userId = UUID.fromString(jwt.subject)
        val page = favorites.list(userId)
        val mine = favorites.ids(userId)
        val body = FavoritesResponse(
            programs = page.programs.map { FavoriteProgramResponse(ProgramSummaryResponse.from(it.program, language, mine), it.favoritedAt, it.newRecruitment, it.notifyLevel.name) },
            activities = page.activities.map {
                FavoriteActivityResponse(ActivityResponse.from(it.listing, language, mine), ProgramSummaryResponse.from(it.program, language, mine), it.favoritedAt, it.notifyLevel.name)
            },
        )
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, language.tag).body(body)
    }

    // 본문이 없으면 기본 수준(신청일만)으로 넣고, 이미 있으면 notifyLevel이 있을 때만 바꾼다
    @PutMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun put(@AuthenticationPrincipal jwt: Jwt, @PathVariable kind: String, @PathVariable id: Long, @RequestBody(required = false) request: FavoriteRequest?) {
        val level = request?.notifyLevel?.let { value ->
            NotifyLevel.entries.firstOrNull { it.name == value } ?: throw InvalidInputException("notifyLevel은 ALL, APPLICATION, NONE 중 하나입니다")
        }
        favorites.put(UUID.fromString(jwt.subject), target(kind), id, level)
    }

    @DeleteMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable kind: String, @PathVariable id: Long) =
        favorites.delete(UUID.fromString(jwt.subject), target(kind), id)

    // 즐겨찾기 화면에서 그 프로그램을 열면 newRecruitment를 끈다
    @PostMapping("/programs/{id}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun seen(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = favorites.seen(UUID.fromString(jwt.subject), id)

    private fun target(kind: String) = when (kind) {
        "activities" -> FavoriteTarget.ACTIVITY
        "programs" -> FavoriteTarget.PROGRAM
        else -> throw NotFoundException("경로를 찾을 수 없습니다", "Unknown favorite kind: $kind")
    }
}

data class FavoriteRequest(val notifyLevel: String? = null)

data class FavoriteProgramResponse(val program: ProgramSummaryResponse, val favoritedAt: OffsetDateTime, val newRecruitment: Boolean, val notifyLevel: String)

data class FavoriteActivityResponse(val activity: ActivityResponse, val program: ProgramSummaryResponse, val favoritedAt: OffsetDateTime, val notifyLevel: String)

// 프로그램은 즐겨찾기한 최근 순, 활동은 마감 임박 순(지난 활동은 뒤)
data class FavoritesResponse(val programs: List<FavoriteProgramResponse>, val activities: List<FavoriteActivityResponse>)
