package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.application.profile.ContactInput
import io.wid.dearby.application.profile.HistoryInput
import io.wid.dearby.application.profile.JobInput
import io.wid.dearby.application.profile.ProfileInput
import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.domain.InvalidInputException
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

// native-v1.md "프로필과 버전 이력". 로그인 사용자 자신의 프로필만
@RestController
@RequestMapping("/v1/profile")
class ProfileController(private val profiles: ProfileService) {

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt) = response(userId(jwt), profiles.get(userId(jwt)))

    // If-Match: 마지막으로 받은 version. 다르면 409
    @PutMapping
    fun put(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ProfileRequest,
        @RequestHeader("If-Match", required = false) ifMatch: String?,
    ): ProfileResponse {
        val expected = ifMatch?.trim()?.trim('"')?.let { it.toIntOrNull() ?: throw InvalidInputException("If-Match는 버전 번호입니다") }
        return response(userId(jwt), profiles.update(userId(jwt), toInput(request), expected))
    }

    @GetMapping("/versions")
    fun versions(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) cursor: Int?,
        @RequestParam(defaultValue = "20") size: Int,
    ): VersionPageResponse {
        val (items, next) = profiles.versions(userId(jwt), cursor, size)
        return VersionPageResponse(items.map { (version, at) -> VersionSummaryResponse(version, at) }, next)
    }

    @GetMapping("/versions/{version}")
    fun version(@AuthenticationPrincipal jwt: Jwt, @PathVariable version: Int) = VersionResponse.from(profiles.version(userId(jwt), version))

    @PostMapping("/versions/{version}/restore")
    fun restore(@AuthenticationPrincipal jwt: Jwt, @PathVariable version: Int) = response(userId(jwt), profiles.restore(userId(jwt), version))

    // 프로필 사진(native-v1.md "프로필 사진"). 본인만. 응답의 photoUrl은 바뀔 때마다 v가 달라 오래 캐시해도 된다
    @PutMapping("/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun putPhoto(@AuthenticationPrincipal jwt: Jwt, @RequestPart("file") file: MultipartFile): PhotoResponse {
        val contentType = file.contentType ?: throw InvalidInputException("파일 종류를 알 수 없습니다")
        return PhotoResponse(photoUrl(profiles.putPhoto(userId(jwt), contentType, file.bytes)))
    }

    @GetMapping("/photo")
    fun photo(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
        .body(profiles.photo(userId(jwt)))

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePhoto(@AuthenticationPrincipal jwt: Jwt) = profiles.deletePhoto(userId(jwt))

    data class PhotoResponse(val photoUrl: String)

    private fun photoUrl(updatedAt: OffsetDateTime) = "/v1/profile/photo?v=${updatedAt.toInstant().toEpochMilli()}"

    private fun toInput(r: ProfileRequest) = ProfileInput(
        name = r.name,
        jobs = r.jobs.map { JobInput(it.id, it.jobId, it.jobName) },
        introLink = r.introLink,
        contacts = r.contacts.map { ContactInput(it.id, it.kind, it.label, it.value) },
        histories = r.histories.map {
            HistoryInput(
                it.id, it.kind, it.organizationId, it.organizationName, it.programId, it.programName,
                it.activityId, it.activityName, it.roleId, it.roleName, it.startDate, it.endDate, it.description,
            )
        },
    )

    private fun response(userId: UUID, profile: io.wid.dearby.domain.profile.Profile) = ProfileResponse.from(profile, profiles.consent(userId), profiles.photoUpdatedAt(userId)?.let(::photoUrl))

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
