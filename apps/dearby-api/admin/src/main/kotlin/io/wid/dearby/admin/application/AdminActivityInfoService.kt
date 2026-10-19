package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.application.collection.CollectionSettingsRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.*
import io.wid.dearby.domain.curation.CollectionSettings
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

// 관리자 활동 정보 작업(shared/contracts/admin-v1.md). 목록은 id 오름차순 keyset 페이지
@Service
class AdminActivityInfoService(
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
    fun createOrganization(name: String, nameEn: String?, parentOrganizationId: Id?): OrganizationListing {
        val trimmed = requireName(name)
        val english = optionalText(nameEn, null)
        parentOrganizationId?.let(::requireOrganization)
        requireUniqueOrganizationName(parentOrganizationId, trimmed, english, null)
        val id = unique { organizations.create(trimmed, english, parentOrganizationId) }
        return organizationListing(id)
    }

    // 보낸 값만 바꾼다(nameEn은 빈 문자열이면 지운다). 자기 자신이나 자기 하위 조직을 상위로 지정하면 순환이라 거부한다
    @Transactional
    fun updateOrganization(id: Id, name: String?, nameEn: String?, parentOrganizationId: Id?, clearParent: Boolean): OrganizationListing {
        val current = organizations.findAllById(listOf(id)).singleOrNull()
            ?: throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $id")
        val parent = if (clearParent) null else parentOrganizationId ?: current.parentOrganizationId
        if (parent != null && parent != current.parentOrganizationId) {
            requireOrganization(parent)
            if (parent == id || id in organizations.ancestorIds(parent)) {
                throw InvalidInputException("자기 자신이나 하위 조직 아래로 옮길 수 없습니다", "Cycle: $id under $parent")
            }
        }
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = optionalText(nameEn, current.nameEn)
        if (newName != current.name || newNameEn != current.nameEn || parent != current.parentOrganizationId) {
            requireUniqueOrganizationName(parent, newName, newNameEn, id)
        }
        unique { organizations.update(id, newName, newNameEn, parent) }
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
    fun createProgram(organizationId: Id, name: String, nameEn: String?, collection: CollectionSettings?): ProgramListing {
        val trimmed = requireName(name)
        val english = optionalText(nameEn, null)
        requireOrganization(organizationId)
        requireUniqueProgramName(organizationId, trimmed, english, null)
        val id = unique { programs.create(organizationId, trimmed, english) }
        collection?.let { settings.save(id, validate(it)) }
        return listing(id)
    }

    // 하위 조직이나 프로그램이 있으면 지우지 않는다(논리키라 남은 것이 주인 없는 데이터가 된다)
    @Transactional
    fun deleteOrganization(id: Id) {
        if (organizations.findAllById(listOf(id)).isEmpty()) throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $id")
        if ((organizations.childCounts(listOf(id))[id] ?: 0) > 0 || (organizations.programCounts(listOf(id))[id] ?: 0) > 0) {
            throw InvalidInputException("하위 조직이나 프로그램이 있어 삭제할 수 없습니다. 먼저 옮기거나 지워 주세요")
        }
        organizations.delete(id)
    }

    // 활동이 있으면 지우지 않는다. 수집 설정·수집 기록은 함께 지운다
    @Transactional
    fun deleteProgram(id: Id) {
        if (programs.findAllById(listOf(id)).isEmpty()) throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $id")
        if (programs.activityCount(id) > 0) throw InvalidInputException("활동이 있어 삭제할 수 없습니다. 먼저 활동을 지워 주세요")
        programs.delete(id)
    }

    // 수집으로 생긴 활동은 다음 수집에서 다시 만들어지지 않는다
    @Transactional
    fun deleteActivity(id: Id) {
        if (!curations.delete(id)) throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $id")
    }

    // 보낸 값만 바꾼다(nameEn은 빈 문자열이면 지운다)
    @Transactional
    fun updateProgram(id: Id, name: String?, nameEn: String?, organizationId: Id?): ProgramListing {
        val current = programs.findAllById(listOf(id)).singleOrNull()
            ?: throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $id")
        organizationId?.let(::requireOrganization)
        val newOrganization = organizationId ?: current.organizationId
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = optionalText(nameEn, current.nameEn)
        if (newName != current.name || newNameEn != current.nameEn || newOrganization != current.organizationId) {
            requireUniqueProgramName(newOrganization, newName, newNameEn, id)
        }
        unique { programs.update(id, newOrganization, newName, newNameEn) }
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

    // 보낸 값만 바꾼다. 영문 값은 빈 문자열이면 지운다. 한글 이름은 비울 수 없다.
    // eligibility(native-v1.md "대상자 조건")는 Keep이면 그대로, Clear면 지우고, Set이면 어휘 검증을 지난 값으로 바꾼다
    @Transactional
    fun updateActivityTexts(
        activityId: Id,
        name: String?,
        nameEn: String?,
        description: String?,
        descriptionEn: String?,
        eligibility: EligibilityChange = EligibilityChange.Keep,
        activitySchedule: ScheduleChange = ScheduleChange.Keep,
        application: ScheduleChange = ScheduleChange.Keep,
        officialUrl: String? = null,
        applicationUrl: String? = null,
    ): AdminActivity {
        val current = activities.getById(activityId, publicationStatus = null).activity
        curations.updateTexts(
            activityId,
            name = name?.let(::requireName) ?: current.name,
            nameEn = optionalText(nameEn, current.nameEn),
            description = description?.trim() ?: current.description,
            descriptionEn = optionalText(descriptionEn, current.descriptionEn),
            // 공식 안내·신청 링크(2026-10-19): 보낸 값만 바꾼다. 공식 안내는 https 필수, 신청 링크는 빈 문자열이면 지운다
            officialUrl = officialUrl?.let { requireHttps(it.trim(), "공식 안내 URL") } ?: current.officialUrl,
            applicationUrl = if (applicationUrl == null) curations.findAllByActivityId(listOf(activityId)).firstOrNull()?.applicationUrl
                else applicationUrl.trim().ifEmpty { null }?.let { requireHttps(it, "신청 링크") },
            touched = setOfNotNull(
                name?.let { "name" }, nameEn?.let { "nameEn" }, description?.let { "description" }, descriptionEn?.let { "descriptionEn" },
                officialUrl?.let { "officialUrl" }, applicationUrl?.let { "applicationUrl" },
            ),
        )
        when (eligibility) {
            EligibilityChange.Keep -> Unit
            EligibilityChange.Clear -> curations.updateEligibility(activityId, null)
            is EligibilityChange.Set -> curations.updateEligibility(activityId, eligibility.group)
        }
        // 일정 직접 지정(admin-v1.md): 날짜만이면 종일, 시각까지면 시각 일정. null이면 구조화된 값을 지우고 문구만 남긴다
        applySchedule(activityId, ScheduleSlot.ACTIVITY, activitySchedule)
        applySchedule(activityId, ScheduleSlot.APPLICATION, application)
        return withCuration(listOf(activities.getById(activityId, publicationStatus = null))).single()
    }

    private fun applySchedule(activityId: Id, slot: ScheduleSlot, change: ScheduleChange) {
        when (change) {
            ScheduleChange.Keep -> Unit
            ScheduleChange.Clear -> curations.updateSchedule(activityId, slot, null)
            is ScheduleChange.Set -> curations.updateSchedule(activityId, slot, change.edit.toTime())
        }
    }

    private fun withCuration(items: List<ActivityListing>): List<AdminActivity> {
        val byId = curations.findAllByActivityId(items.map { it.activity.id }).associateBy { it.activityId }
        return items.mapNotNull { listing -> byId[listing.activity.id]?.let { AdminActivity(listing, it) } }
    }

    // 같은 계위에서 띄어쓰기를 뺀 이름이 같으면 중복(2026-10-15 사용자 결정)
    // 영문 이름도 같은 규칙으로 영문 이름끼리 비교한다(V13)
    private fun requireUniqueOrganizationName(parentOrganizationId: Id?, name: String, nameEn: String?, exceptId: Id?) {
        fun duplicate(kind: String) = InvalidInputException(
            if (parentOrganizationId == null) "같은 ${kind}의 최상위 조직이 있습니다" else "같은 상위 조직 아래에 같은 ${kind}의 조직이 있습니다"
        )
        if (organizations.existsSiblingName(parentOrganizationId, name, exceptId)) throw duplicate("이름")
        if (nameEn != null && organizations.existsSiblingName(parentOrganizationId, nameEn, exceptId, english = true)) throw duplicate("영문 이름")
    }

    private fun requireUniqueProgramName(organizationId: Id, name: String, nameEn: String?, exceptId: Id?) {
        if (programs.existsName(organizationId, name, exceptId)) throw InvalidInputException("같은 조직에 같은 이름의 프로그램이 있습니다")
        if (nameEn != null && programs.existsName(organizationId, nameEn, exceptId, english = true)) {
            throw InvalidInputException("같은 조직에 같은 영문 이름의 프로그램이 있습니다")
        }
    }

    // 확인과 저장 사이에 같은 이름이 동시에 들어오면 DB 유일 인덱스(V12)가 막는다. 같은 안내로 바꾼다
    private fun <T> unique(write: () -> T): T = try {
        write()
    } catch (e: DuplicateKeyException) {
        throw InvalidInputException("같은 계위에 같은 이름이 있습니다", e.message ?: "duplicate name")
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
        val ancestors = organizations.ancestors(ids)
        return page.map { OrganizationListing(it, children[it.id] ?: 0, programCounts[it.id] ?: 0, ancestors[it.id].orEmpty()) }
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

    // null이면 지금 값 그대로, 빈 문자열이면 지운다(선택 입력인 영문 값)
    private fun requireHttps(url: String, field: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.isEmpty() || uri?.scheme != "https" || uri.host.isNullOrBlank()) throw InvalidInputException("${field}은(는) https 주소여야 합니다")
        return url
    }

    private fun optionalText(value: String?, current: String?): String? =
        if (value == null) current else value.trim().ifEmpty { null }

    private fun requireName(name: String): String =
        name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }

    private fun checkSize(size: Int) {
        if (size !in 1..ActivityService.MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${ActivityService.MAX_PAGE_SIZE} 사이여야 합니다")
    }

    private companion object {
        val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    }
}
