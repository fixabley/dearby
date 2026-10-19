package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.application.profile.ContactInput
import io.wid.dearby.application.profile.HistoryInput
import io.wid.dearby.application.profile.JobInput
import io.wid.dearby.application.profile.ProfileInput
import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.domain.InvalidInputException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
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

    private fun toInput(r: ProfileRequest) = ProfileInput(
        name = r.name,
        jobs = r.jobs.map { JobInput(it.id, it.jobId, it.jobName) },
        introduction = r.introduction,
        contacts = r.contacts.map { ContactInput(it.id, it.kind, it.label, it.value) },
        histories = r.histories.map {
            HistoryInput(
                it.id, it.kind, it.organizationId, it.organizationName, it.programId, it.programName,
                it.activityId, it.activityName, it.roleId, it.roleName, it.startDate, it.endDate, it.description,
            )
        },
    )

    private fun response(userId: UUID, profile: io.wid.dearby.domain.profile.Profile) = ProfileResponse.from(profile, profiles.consent(userId))

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
