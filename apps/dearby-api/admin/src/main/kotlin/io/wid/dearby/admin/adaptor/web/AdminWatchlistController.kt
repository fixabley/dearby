package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.WatchlistAdminService
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.collection.WatchKind
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

// admin-v1.md "조심 목록". 모든 경로는 ADMIN 역할만
@RestController
@RequestMapping("/v1/admin")
class AdminWatchlistController(private val service: WatchlistAdminService) {

    @GetMapping("/programs/{id}/watchlist")
    fun list(@PathVariable id: Long) = WatchlistResponse(service.list(id).map(WatchEntryResponse::from))

    @PostMapping("/programs/{id}/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable id: Long, @RequestBody request: WatchEntryRequest) =
        WatchEntryResponse.from(service.create(id, request.name, kind(request.kind), request.canonicalActivityId, request.reason))

    @DeleteMapping("/watchlist/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable entryId: Long) = service.delete(entryId)

    @PostMapping("/activities/{id}/duplicate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markDuplicate(@PathVariable id: Long, @RequestBody request: DuplicateRequest) =
        service.markDuplicate(id, kind(request.kind), request.canonicalActivityId, request.reason)

    private fun kind(value: String): WatchKind =
        WatchKind.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("종류는 alias 또는 exclude입니다")
}

// kind는 소문자 alias|exclude(계약)로 받는다
data class WatchEntryRequest(val name: String, val kind: String, val canonicalActivityId: Long? = null, val reason: String? = null)

data class DuplicateRequest(val kind: String, val canonicalActivityId: Long? = null, val reason: String? = null)

data class WatchlistResponse(val items: List<WatchEntryResponse>)

data class WatchEntryResponse(
    val id: Long,
    val programId: Long,
    val name: String,
    val normalizedName: String,
    val kind: String,
    val canonicalActivityId: Long?,
    val canonicalActivityName: String?,
    val reason: String?,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(entry: WatchEntry) = WatchEntryResponse(
            entry.id, entry.programId, entry.name, entry.normalizedName, entry.kind.name.lowercase(),
            entry.canonicalActivityId, entry.canonicalActivityName, entry.reason, entry.createdAt,
        )
    }
}
