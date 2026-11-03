package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.RoleAdminService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.profile.RoleOwnerKind
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 역할(admin-v1.md "이력 검토·역할"): 조직·프로그램·활동 중 하나에 귀속. 전역 목록은 없다. ADMIN만
@RestController
@RequestMapping("/v1/admin/roles")
class RoleAdminController(private val roles: RoleAdminService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) programId: Long?,
        @RequestParam(required = false) activityId: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<RoleResponse> {
        val (kind, ownerId) = owner(organizationId, programId, activityId)
        return roles.search(kind, ownerId, q, cursor, size).let { page -> PageResponse(page.items.map(RoleResponse::from), page.nextCursor) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: RoleRequest): RoleResponse {
        val (kind, ownerId) = owner(request.organizationId, request.programId, request.activityId)
        return RoleResponse.from(roles.create(kind, ownerId, request.name ?: throw InvalidInputException("이름을 입력해 주세요"), request.nameEn))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: RoleRequest) = RoleResponse.from(roles.update(id, request.name, request.nameEn, request.position))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = roles.delete(id)

    private fun owner(organizationId: Long?, programId: Long?, activityId: Long?): Pair<RoleOwnerKind, Long> =
        listOfNotNull(organizationId?.let { RoleOwnerKind.ORGANIZATION to it }, programId?.let { RoleOwnerKind.PROGRAM to it }, activityId?.let { RoleOwnerKind.ACTIVITY to it })
            .singleOrNull() ?: throw InvalidInputException("organizationId, programId, activityId 중 하나를 주세요")
}
