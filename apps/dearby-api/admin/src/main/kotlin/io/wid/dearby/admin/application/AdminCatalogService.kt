package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.collection.CollectionSettingsRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

// 관리자 카탈로그 작업(shared/contracts/admin-v1.md). 목록은 id 오름차순 keyset 페이지
@Service
class AdminCatalogService(
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val settings: CollectionSettingsRepository,
    private val activities: ActivityService,
    private val curations: ActivityCurationRepository,
) {

    @Transactional(readOnly = true)
    fun searchOrganizations(q: String?, parentId: Id?, rootsOnly: Boolean, cursor: Id?, size: Int): Page<OrganizationListing> {
        checkSize(size)
        val found = organizations.search(q, parentId, rootsOnly, cursor, size + 1)
        val page = found.take(size)
        return Page(organizationListings(page), if (found.size > size) page.last().id else null)
    }

    @Transactional
    fun createOrganization(name: String, parentOrganizationId: Id?): OrganizationListing {
        val trimmed = requireName(name)
        parentOrganizationId?.let(::requireOrganization)
        val id = organizations.create(trimmed, parentOrganizationId)
        return organizationListing(id)
    }

    // 보낸 값만 바꾼다. 자기 자신이나 자기 하위 조직을 상위로 지정하면 순환이라 거부한다
    @Transactional
    fun updateOrganization(id: Id, name: String?, parentOrganizationId: Id?, clearParent: Boolean): OrganizationListing {
        val current = organizations.findAllById(listOf(id)).singleOrNull()
            ?: throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $id")
        val parent = if (clearParent) null else parentOrganizationId ?: current.parentOrganizationId
        if (parent != null && parent != current.parentOrganizationId) {
            requireOrganization(parent)
            if (parent == id || id in organizations.ancestorIds(parent)) {
                throw InvalidInputException("자기 자신이나 하위 조직 아래로 옮길 수 없습니다", "Cycle: $id under $parent")
            }
        }
        organizations.update(id, name?.let(::requireName) ?: current.name, parent)
        return organizationListing(id)
    }

    @Transactional(readOnly = true)
    fun searchPrograms(q: String?, organizationId: Id?, cursor: Id?, size: Int): Page<ProgramListing> {
        checkSize(size)
        val found = programs.search(q, organizationId, cursor, size + 1)
        val page = found.take(size)
        return Page(listings(page), if (found.size > size) page.last().id else null)
    }

    @Transactional
    fun createProgram(organizationId: Id, name: String, collection: CollectionSettings?): ProgramListing {
        val trimmed = requireName(name)
        requireOrganization(organizationId)
        val id = programs.create(organizationId, trimmed)
        collection?.let { settings.save(id, validate(it)) }
        return listing(id)
    }

    @Transactional
    fun updateProgram(id: Id, name: String?, organizationId: Id?): ProgramListing {
        val current = programs.findAllById(listOf(id)).singleOrNull()
            ?: throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $id")
        organizationId?.let(::requireOrganization)
        programs.update(id, organizationId ?: current.organizationId, name?.let(::requireName) ?: current.name)
        return listing(id)
    }

    @Transactional(readOnly = true)
    fun getCollectionSettings(programId: Id): ProgramListing = listing(programId)

    @Transactional
    fun putCollectionSettings(programId: Id, collection: CollectionSettings): ProgramListing {
        listing(programId)
        settings.save(programId, validate(collection))
        return listing(programId)
    }

    @Transactional(readOnly = true)
    fun searchActivities(condition: ActivitySearch): Page<AdminActivity> {
        val page = activities.search(condition)
        return Page(withCuration(page.items), page.nextCursor)
    }

    @Transactional
    fun setPublicationStatus(activityId: Id, status: PublicationStatus): AdminActivity {
        if (!curations.updatePublicationStatus(activityId, status)) {
            throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $activityId")
        }
        return withCuration(listOf(activities.getById(activityId, publicationStatus = null))).single()
    }

    private fun withCuration(items: List<ActivityListing>): List<AdminActivity> {
        val byId = curations.findAllByActivityId(items.map { it.activity.id }).associateBy { it.activityId }
        return items.mapNotNull { listing -> byId[listing.activity.id]?.let { AdminActivity(listing, it) } }
    }

    private fun requireOrganization(id: Id) {
        if (organizations.findAllById(listOf(id)).isEmpty()) {
            throw InvalidInputException("조직을 찾을 수 없습니다", "Organization not found: $id")
        }
    }

    private fun organizationListing(id: Id): OrganizationListing =
        organizationListings(organizations.findAllById(listOf(id))).single()

    private fun organizationListings(page: List<Organization>): List<OrganizationListing> {
        val ids = page.map { it.id }
        val children = organizations.childCounts(ids)
        val programCounts = organizations.programCounts(ids)
        return page.map { OrganizationListing(it, children[it.id] ?: 0, programCounts[it.id] ?: 0) }
    }

    private fun listing(programId: Id): ProgramListing =
        listings(programs.findAllById(listOf(programId))).singleOrNull()
            ?: throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $programId")

    private fun listings(page: List<Program>): List<ProgramListing> {
        val ids = page.map { it.id }
        val organizationById = organizations.findAllById(page.map { it.organizationId }.toSet()).associateBy { it.id }
        val settingsById = settings.findAllByProgramId(ids)
        val lastSucceeded = settings.lastSucceededOn(ids)
        return page.map {
            ProgramListing(
                program = it,
                organization = organizationById[it.organizationId],
                settings = settingsById[it.id] ?: CollectionSettings.DEFAULT,
                configured = it.id in settingsById,
                lastSucceededOn = lastSucceeded[it.id],
            )
        }
    }

    // officialUrl은 https, 허용 호스트는 스킴·경로·포트 없는 호스트 이름(소문자로 저장)
    private fun validate(collection: CollectionSettings): CollectionSettings {
        val url = collection.officialUrl?.trim()?.ifEmpty { null }
        if (url != null && runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrEmpty() } != true) {
            throw InvalidInputException("공식 안내 페이지는 https 주소여야 합니다")
        }
        val hosts = collection.allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        hosts.firstOrNull { !HOST.matches(it) }?.let { throw InvalidInputException("허용 호스트는 도메인 이름만 씁니다: $it") }
        return collection.copy(officialUrl = url, allowedHosts = hosts)
    }

    private fun requireName(name: String): String =
        name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }

    private fun checkSize(size: Int) {
        if (size !in 1..ActivityService.MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${ActivityService.MAX_PAGE_SIZE} 사이여야 합니다")
    }

    private companion object {
        val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    }
}
