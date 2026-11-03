package io.wid.dearby.application.organization

import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.domain.ActivityStatus
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.Organization
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 조직 화면(native-v1.md "조직 화면·조직 팔로우"): 머리(로고·이름)·조직 경로·프로그램 묶음·하위 조직·개수·팔로우 여부
@Service
class OrganizationService(
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val summaries: ProgramSummaryService,
    private val logos: OrganizationLogos,
    private val favorites: FavoriteService,
) {

    data class Child(val organization: Organization, val logoUrl: String?)

    // counts: programs = 이 조직의 프로그램 수, recruiting = 그 프로그램들의 모집 중 활동 수, past = 종료된 활동 수(게시 활동 전체 기준)
    data class Counts(val programs: Int, val recruiting: Int, val past: Int)

    data class Detail(
        val organization: Organization,
        val logoUrl: String?,
        val brandColor: String?,
        val ancestors: List<Pair<Id, String>>, // 최상위부터
        val children: List<Child>,
        val programs: List<ProgramSummary>, // 모집 중 먼저, 그 뒤 최근 활동 순(ProgramSummaryService.byIds). 게시 활동이 없는 프로그램은 빠진다
        val counts: Counts,
        val followed: Boolean,
    )

    @Transactional(readOnly = true)
    fun detail(id: Id, userId: UserId?): Detail {
        val organization = organizations.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $id")
        val children = organizations.search(null, id, rootsOnly = false, after = null, size = MAX_CHILDREN)
        val logo = logos.of(listOf(id) + children.map { it.id })
        val own = programs.findByOrganizationIds(listOf(id))
        val summaryById = summaries.byIds(own.map { it.id })
        val ordered = own.mapNotNull { summaryById[it.id] }.sortedWith(compareByDescending<ProgramSummary> { it.openActivityCount > 0 }.thenByDescending { it.latestActivityAt }.thenBy { it.name })
        return Detail(
            organization = organization,
            logoUrl = logo[id]?.url,
            brandColor = logo[id]?.brandColor,
            ancestors = organizations.ancestors(listOf(id))[id].orEmpty(),
            children = children.map { Child(it, logo[it.id]?.url) },
            programs = ordered,
            counts = Counts(own.size, ordered.sumOf { it.openActivityCount }, ordered.sumOf { it.statusCounts[ActivityStatus.ENDED] ?: 0 }),
            followed = userId?.let { id in favorites.ids(it).organizations } ?: false,
        )
    }

    // 조직 경로(최상위부터 바로 위 조직까지). 없는 조직은 404
    @Transactional(readOnly = true)
    fun ancestors(id: Id): List<Pair<Id, String>> {
        organizations.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $id")
        return organizations.ancestors(listOf(id))[id].orEmpty()
    }

    private companion object {
        const val MAX_CHILDREN = 100
    }
}
