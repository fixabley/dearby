package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.RoleAdminService
import io.wid.dearby.domain.InvalidInputException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 역할 카탈로그(native-v1.md "관리자"). ADMIN만
@RestController
@RequestMapping("/v1/admin/roles")
class RoleAdminController(private val roles: RoleAdminService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ) = roles.search(q, cursor, size).let { page -> PageResponse(page.items.map(RoleResponse::from), page.nextCursor) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: RoleRequest) =
        RoleResponse.from(roles.create(request.name ?: throw InvalidInputException("이름을 입력해 주세요"), request.nameEn))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: RoleRequest) = RoleResponse.from(roles.update(id, request.name, request.nameEn))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = roles.delete(id)
}
