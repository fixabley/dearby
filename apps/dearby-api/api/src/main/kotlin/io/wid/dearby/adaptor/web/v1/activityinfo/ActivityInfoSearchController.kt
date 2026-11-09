package io.wid.dearby.adaptor.web.v1.activityinfo

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.profile.JobRepository
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.profile.RoleOwnerKind
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
    private val jobs: JobRepository,
    private val activities: ActivityRepository,
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

    // 한 단위(조직·프로그램·활동 중 하나)의 역할. 전역 목록은 없다(native-v1.md "이력 (History) — 역할 매칭"): 귀속이 없으면 빈 페이지(옛 앱 호환), 둘 이상이면 400.
    // 활동은 게시된 것만(초안 노출 방지)
    @GetMapping("/roles")
    @Transactional(readOnly = true)
    fun roles(
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) programId: Long?,
        @RequestParam(required = false) activityId: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): RolePageResponse {
        checkSize(size)
        val owners = listOfNotNull(organizationId?.let { RoleOwnerKind.ORGANIZATION to it }, programId?.let { RoleOwnerKind.PROGRAM to it }, activityId?.let { RoleOwnerKind.ACTIVITY to it })
        if (owners.size > 1) throw InvalidInputException("organizationId, programId, activityId 중 하나만 주세요")
        val (kind, ownerId) = owners.singleOrNull() ?: return RolePageResponse(emptyList(), null)
        if (kind == RoleOwnerKind.ACTIVITY && activities.findById(ownerId, PublicationStatus.PUBLISHED) == null) return RolePageResponse(emptyList(), null)
        val found = roles.search(kind, ownerId, q, cursor, size + 1)
        val page = found.take(size)
        return RolePageResponse(page.map(RoleResponse::from), if (found.size > size) page.last().id else null)
    }

    // 직무(계층). parent는 바로 위 직무
    @GetMapping("/jobs")
    @Transactional(readOnly = true)
    fun jobs(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ActivityInfoPageResponse {
        checkSize(size)
        val found = jobs.search(q, parentId, rootsOnly, cursor, size + 1)
        val page = found.take(size)
        val parentById = jobs.findAllById(page.mapNotNull { it.parentId }.toSet()).associateBy { it.id }
        return ActivityInfoPageResponse(
            page.map { ActivityInfoItemResponse(it.id, it.name, it.nameEn, parent = parentById[it.parentId]?.let { p -> ActivityInfoParentResponse(p.id, p.name) }) },
            if (found.size > size) page.last().id else null,
        )
    }

    private fun checkSize(size: Int) {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
    }
}
