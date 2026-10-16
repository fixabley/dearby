package io.wid.dearby.adaptor.web.v1.activityinfo

import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.InvalidInputException
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 이력 입력의 선택 창용 검색(native-v1.md "이력"). 로그인 필요. 활동은 /v1/activities
@RestController
@RequestMapping("/v1")
class ActivityInfoSearchController(
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val roles: RoleRepository,
) {

    @GetMapping("/organizations")
    @Transactional(readOnly = true)
    fun organizations(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ActivityInfoPageResponse {
        checkSize(size)
        val found = organizations.search(q, parentId, rootsOnly, cursor, size + 1)
        val page = found.take(size)
        return ActivityInfoPageResponse(
            page.map { ActivityInfoItemResponse(it.id, it.name, it.nameEn, parentOrganizationId = it.parentOrganizationId) },
            if (found.size > size) page.last().id else null,
        )
    }

    @GetMapping("/programs")
    @Transactional(readOnly = true)
    fun programs(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ActivityInfoPageResponse {
        checkSize(size)
        val found = programs.search(q, organizationId, cursor, size + 1)
        val page = found.take(size)
        val organizationById = organizations.findAllById(page.map { it.organizationId }.toSet()).associateBy { it.id }
        return ActivityInfoPageResponse(
            page.map { ActivityInfoItemResponse(it.id, it.name, it.nameEn, organization = organizationById[it.organizationId]?.let { o -> ActivityInfoParentResponse(o.id, o.name) }) },
            if (found.size > size) page.last().id else null,
        )
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    fun roles(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ActivityInfoPageResponse {
        checkSize(size)
        val found = roles.search(q, cursor, size + 1)
        val page = found.take(size)
        return ActivityInfoPageResponse(page.map { ActivityInfoItemResponse(it.id, it.name, it.nameEn) }, if (found.size > size) page.last().id else null)
    }

    private fun checkSize(size: Int) {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
    }
}
