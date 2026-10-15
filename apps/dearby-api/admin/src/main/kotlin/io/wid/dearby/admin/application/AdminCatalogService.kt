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
    fun searchOrganizations(q: String?, cursor: Id?, size: Int): Page<Organization> {
        checkSize(size)
        val found = organizations.search(q, cursor, size + 1)
        return page(found, size) { it.id }
    }

    @Transactional
    fun createOrganization(name: String, parentOrganizationId: Id?): Organization {
        val trimmed = requireName(name)
        if (parentOrganizationId != null && organizations.findAllById(listOf(parentOrganizationId)).isEmpty()) {
            throw InvalidInputException("상위 조직을 찾을 수 없습니다", "Parent organization not found: $parentOrganizationId")
        }
        val id = organizations.create(trimmed, parentOrganizationId)
        return organizations.findAllById(listOf(id)).single()
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
        if (organizations.findAllById(listOf(organizationId)).isEmpty()) {
            throw InvalidInputException("조직을 찾을 수 없습니다", "Organization not found: $organizationId")
        }
        val id = programs.create(organizationId, trimmed)
        collection?.let { settings.save(id, validate(it)) }
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

    private fun <T> page(found: List<T>, size: Int, id: (T) -> Id): Page<T> {
        val page = found.take(size)
        return Page(page, if (found.size > size) id(page.last()) else null)
    }

    private companion object {
        val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    }
}
