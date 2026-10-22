package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ProgressStatusRepository
import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.activity.StoredSchedule
import io.wid.dearby.domain.Schedule
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.application.activity.ActivityCreation
import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.collection.CollectionFeedbackRepository
import io.wid.dearby.application.collection.CollectionSettingsRepository
import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.collection.CollectedActivityRef
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.collection.NewFeedback
import io.wid.dearby.domain.collection.CollectionUrlTemplate
import java.time.LocalDate
import java.time.ZoneId
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.tag.TagRepository
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
    private val progressStatuses: ProgressStatusRepository,
    private val drafts: ActivityDraftRepository,
    private val tags: TagRepository,
    private val logos: ProgramLogoRepository,
    private val feedback: CollectionFeedbackRepository,
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
        // 수집 피드백(V43): 지우기 전에 이름을 읽어 둔다(지우면 사라진다). 운영자가 직접 만든 활동은 남기지 않는다
        val collected = feedback.collectedActivity(id)
        if (!curations.delete(id)) throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $id")
        collected?.let { feedback.record(NewFeedback(FeedbackKind.DRAFT_DISMISSED, it.programId, it.host, it.name, emptyMap(), activityId = id)) }
    }

    // 보낸 값만 바꾼다(nameEn은 빈 문자열이면 지운다)
    @Transactional
    fun updateProgram(
        id: Id, name: String?, nameEn: String?, organizationId: Id?,
        category: CategoryChange = CategoryChange.Keep, applyCategoryToActivities: Boolean = false,
        tagIds: List<Id>? = null, applyTagsToActivities: Boolean = false,
    ): ProgramUpdate {
        val current = programs.findAllById(listOf(id)).singleOrNull()
            ?: throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $id")
        organizationId?.let(::requireOrganization)
        val newOrganization = organizationId ?: current.organizationId
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = optionalText(nameEn, current.nameEn)
        if (newName != current.name || newNameEn != current.nameEn || newOrganization != current.organizationId) {
            requireUniqueProgramName(newOrganization, newName, newNameEn, id)
        }
        // 프로그램 기본 종류(admin-v1.md): 생략은 유지, null은 해제. 일괄 적용은 종류가 있을 때만, 운영자가 지정한 활동은 건너뛴다
        val newCategory = when (category) {
            CategoryChange.Keep -> current.category
            CategoryChange.Clear -> null
            is CategoryChange.Set -> category.category
        }
        unique { programs.update(id, newOrganization, newName, newNameEn, newCategory) }
        val applied = if (applyCategoryToActivities && newCategory != null) curations.applyProgramCategory(id, newCategory) else null
        // 프로그램 태그(native-v1.md "태그"): 생략은 유지, 목록은 통째로 바꾼다. 일괄 적용은 운영자가 태그를 지정하지 않은 활동에 합집합으로
        val newTagIds = tagIds?.let { requireTags(it) } ?: current.tags.map { it.id }
        if (tagIds != null) tags.setProgramTags(id, newTagIds)
        val appliedTags = if (applyTagsToActivities && newTagIds.isNotEmpty()) tags.addProgramTagsToActivities(id, newTagIds) else null
        return ProgramUpdate(listing(id), applied, appliedTags)
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
        category: CategoryChange = CategoryChange.Keep,
        tagIds: List<Id>? = null,
        place: PlaceChange = PlaceChange.Keep,
        activityTiming: TimingChange? = null, // 활동·모집 기간 객체의 겹침 확인 시간·반복 규칙. null이면 그대로
        applicationTiming: TimingChange? = null,
    ): AdminActivity {
        val current = activities.getById(activityId, publicationStatus = null).activity
        val collected = feedback.collectedActivity(activityId)
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
        // 활동 종류(native-v1.md "활동 종류"): 생략은 유지, null은 미분류, 코드는 바꾼다. 운영자 지정 필드 category에 남는다
        when (category) {
            CategoryChange.Keep -> Unit
            CategoryChange.Clear -> curations.updateCategory(activityId, null)
            is CategoryChange.Set -> curations.updateCategory(activityId, category.category)
        }
        // 태그(native-v1.md "태그"): 생략은 유지, 목록은 통째로 바꾸고 운영자 지정 tags에 남긴다
        tagIds?.let { tags.setActivityTags(activityId, requireTags(it), curate = true) }
        // 일정 직접 지정(admin-v1.md): 날짜만이면 종일, 시각까지면 시각 일정. null이면 구조화된 값을 지우고 문구만 남긴다
        applySchedule(activityId, ScheduleSlot.ACTIVITY, activitySchedule)
        applySchedule(activityId, ScheduleSlot.APPLICATION, application)
        // 날짜를 바꿨거나 지웠으면 남은 반복 규칙이 새 날짜와 맞는지도 본다
        applyTiming(activityId, activityTiming, applicationTiming, datesChanged = activitySchedule != ScheduleChange.Keep || application != ScheduleChange.Keep)
        // 활동 장소(admin-v1.md "활동 장소와 지오코딩"): 생략은 유지, null은 네 열 모두 삭제, 객체는 교체. 운영자 지정 place로 보호
        when (place) {
            PlaceChange.Keep -> Unit
            PlaceChange.Clear -> curations.updatePlace(activityId, null)
            is PlaceChange.Set -> curations.updatePlace(activityId, place.place)
        }
        collected?.let { recordCorrections(it, current, name, eligibility, category, tagIds, place, activitySchedule, application) }
        return withCuration(listOf(activities.getById(activityId, publicationStatus = null))).single()
    }

    // 운영자 직접 등록(admin-v1.md "POST /v1/admin/activities", 2026-10-19): 초안, 출처 없음(수집이 건드리지 않음), 공식 출처, 보낸 필드는 모두 운영자 지정
    @Transactional
    fun createActivity(input: ActivityCreateInput): AdminActivity {
        val program = programs.findAllById(listOf(input.programId)).singleOrNull()
            ?: throw InvalidInputException("프로그램을 찾을 수 없습니다", "Program not found: ${input.programId}")
        val name = requireName(input.name)
        if (curations.existsName(input.programId, name)) throw InvalidInputException("같은 프로그램에 같은 이름의 활동이 있습니다")
        val officialUrl = requireHttps(input.officialUrl.trim(), "공식 안내 URL")
        val applicationUrl = input.applicationUrl?.trim()?.ifEmpty { null }?.let { requireHttps(it, "신청 링크") }
        val activityTime = input.activitySchedule?.toTime()
        val dateLabel = input.activityDateLabel?.trim().orEmpty()
        if (activityTime == null && dateLabel.isEmpty()) throw InvalidInputException("활동 기간은 날짜 범위나 원문 문구 중 하나는 있어야 합니다")
        val curated = listOfNotNull(
            "name", "description", "officialUrl",
            input.nameEn?.let { "nameEn" }, input.descriptionEn?.let { "descriptionEn" }, applicationUrl?.let { "applicationUrl" },
            input.participationType?.let { "participationType" }, input.category?.let { "category" },
            input.audience?.let { "audience" }, input.location?.let { "location" }, input.cost?.let { "cost" }, input.eligibility?.let { "eligibility" },
            input.tagIds?.let { "tags" }, input.place?.let { "place" },
        )
        val id = curations.insert(
            ActivityCreation(
                programId = input.programId, name = name, nameEn = optionalText(input.nameEn, null), description = input.description.trim(),
                descriptionEn = optionalText(input.descriptionEn, null), officialUrl = officialUrl, applicationUrl = applicationUrl,
                participationType = input.participationType ?: ParticipationType.REGISTRATION, category = input.category ?: program.category,
                audience = optionalText(input.audience, null), location = optionalText(input.location, null), cost = optionalText(input.cost, null),
                dateLabel = dateLabel, curatedFields = curated,
            )
        )
        activityTime?.let { curations.updateSchedule(id, ScheduleSlot.ACTIVITY, it, input.activitySchedule?.note) }
        input.place?.let { curations.updatePlace(id, it) }
        input.application?.toTime()?.let { curations.updateSchedule(id, ScheduleSlot.APPLICATION, it, input.application.note) }
        applyTiming(id, input.activityTiming, input.applicationTiming, datesChanged = false)
        input.eligibility?.let { curations.updateEligibility(id, it) }
        // 태그(native-v1.md "태그"): 보냈으면 그것(운영자 지정), 생략하면 프로그램 태그를 기본으로
        val tagIds = input.tagIds?.let { requireTags(it) } ?: program.tags.map { it.id }
        if (tagIds.isNotEmpty() || input.tagIds != null) tags.setActivityTags(id, tagIds, curate = input.tagIds != null)
        return withCuration(listOf(activities.getById(id, publicationStatus = null))).single()
    }

    // 세부 일정 편집기(admin-v1.md): 슬롯 일정(모집·활동 기간)은 여기서 고치지 않는다(400)
    @Transactional
    fun createDetailSchedule(activityId: Id, patch: ScheduleItemPatch): Schedule {
        activities.getById(activityId, publicationStatus = null)
        return curations.insertDetailSchedule(activityId, patch.toItem(existing = null))
            ?: throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $activityId")
    }

    @Transactional
    fun updateDetailSchedule(activityId: Id, scheduleId: Id, patch: ScheduleItemPatch): Schedule {
        // 모집·활동 기간 행은 겹침 확인 시간·반복 규칙만 여기서 고친다(native-v1.md "겹침 확인 시간"·"반복 일정과 겹침 결과 묶음")
        if (patch.timingOnly) {
            val schedule = requireSchedule(activityId, scheduleId).schedule
            val (overlap, rule) = patch.timing(schedule, schedule.time)
            return curations.updateTiming(activityId, scheduleId, overlap, rule)
                ?: throw NotFoundException("일정을 찾을 수 없습니다", "Schedule not found: $scheduleId")
        }
        val stored = requireDetailSchedule(activityId, scheduleId)
        val updated = curations.updateDetailSchedule(activityId, scheduleId, patch.toItem(existing = stored.schedule))
            ?: throw NotFoundException("일정을 찾을 수 없습니다", "Schedule not found: $scheduleId")
        // 수집한 일정의 날짜를 운영자가 바꾸면 그 원문 문구의 해석으로 남긴다
        val before = stored.schedule
        if (before.dateLabel.isNotBlank() && updated.time != null && updated.time != before.time) {
            feedback.collectedActivity(activityId)?.let {
                feedback.record(NewFeedback(FeedbackKind.DATE_CURATED, it.programId, it.host, before.dateLabel, mapOf("scheduleId" to scheduleId, "title" to updated.title) + times(updated.time), updated.curatedNote, activityId))
            }
        }
        return updated
    }

    @Transactional
    fun deleteDetailSchedule(activityId: Id, scheduleId: Id) {
        requireDetailSchedule(activityId, scheduleId)
        if (!curations.deleteDetailSchedule(activityId, scheduleId)) throw NotFoundException("일정을 찾을 수 없습니다", "Schedule not found: $scheduleId")
    }

    // 일정 상태(native-v1.md "내 활동 서버 저장·일정 상태"): 모집·활동 기간을 포함한 모든 일정. 어드민 지정이 없으면 기본 세 개(id null)
    @Transactional(readOnly = true)
    fun scheduleStatuses(activityId: Id, scheduleId: Id): List<ProgressStatus> = requireSchedule(activityId, scheduleId).schedule.statuses

    // 목록을 통째로 바꾼다: 0~8개(빈 목록은 기본 세 개로 되돌림), 이름 1~20자이고 일정 안에서 겹치지 않음, id는 이 일정의 상태여야 한다.
    // 지운 상태를 고른 사용자 선택은 그 상태의 기본 종류로 옮긴다
    @Transactional
    fun replaceScheduleStatuses(activityId: Id, scheduleId: Id, items: List<ProgressStatus>): List<ProgressStatus> {
        val current = requireSchedule(activityId, scheduleId).schedule.statuses.mapNotNull { it.id }.toSet()
        if (items.size > MAX_STATUSES) throw InvalidInputException("상태는 ${MAX_STATUSES}개까지입니다")
        val cleaned = items.map { it.copy(name = it.name.trim()) }
        cleaned.forEach {
            if (it.name.length !in 1..20) throw InvalidInputException("상태 이름은 1~20자입니다")
            if (it.id != null && it.id !in current) throw InvalidInputException("이 일정의 상태가 아닙니다: ${it.id}")
        }
        if (cleaned.map { it.name }.toSet().size != cleaned.size) throw InvalidInputException("같은 이름의 상태가 있습니다")
        if (cleaned.mapNotNull { it.id }.toSet().size != cleaned.count { it.id != null }) throw InvalidInputException("같은 상태 id가 두 번 있습니다")
        progressStatuses.replace(scheduleId, cleaned)
        return requireSchedule(activityId, scheduleId).schedule.statuses
    }

    private fun requireSchedule(activityId: Id, scheduleId: Id): StoredSchedule =
        curations.findSchedule(activityId, scheduleId) ?: throw NotFoundException("일정을 찾을 수 없습니다", "Schedule not found: $scheduleId for activity $activityId")

    private fun requireDetailSchedule(activityId: Id, scheduleId: Id): StoredSchedule {
        val stored = requireSchedule(activityId, scheduleId)
        if (stored.slot != null) throw InvalidInputException("모집 기간과 활동 기간은 활동 편집의 application·activitySchedule로 고칩니다")
        return stored
    }

    // 수집 피드백(V43): 수집한 활동의 값을 운영자가 바꾼 것만 남긴다. sourceText는 수집기가 썼던 값(검색 대상), 없으면 활동 이름
    private fun recordCorrections(
        collected: CollectedActivityRef, current: Activity, name: String?, eligibility: EligibilityChange, category: CategoryChange,
        tagIds: List<Id>?, place: PlaceChange, activitySchedule: ScheduleChange, application: ScheduleChange,
    ) {
        fun field(field: String, from: Any?, to: Any?) {
            if (from == to) return
            val source = from?.toString()?.takeIf { it.isNotBlank() } ?: current.name
            feedback.record(NewFeedback(FeedbackKind.FIELD_CURATED, collected.programId, collected.host, source, mapOf("field" to field, "from" to from, "to" to to), activityId = current.id))
        }
        name?.let { field("name", current.name, it.trim()) }
        when (category) {
            CategoryChange.Keep -> Unit
            CategoryChange.Clear -> field("category", current.category?.name, null)
            is CategoryChange.Set -> field("category", current.category?.name, category.category.name)
        }
        tagIds?.let { ids -> field("tags", current.tags.map { it.name }, tags.findAllById(ids.distinct()).map { it.name }) }
        when (eligibility) {
            EligibilityChange.Keep -> Unit
            EligibilityChange.Clear -> field("eligibility", current.eligibility?.let(EligibilityJson::write), null)
            is EligibilityChange.Set -> field("eligibility", current.eligibility?.let(EligibilityJson::write), EligibilityJson.write(eligibility.group))
        }
        val currentPlace = current.activitySchedule.address?.addressLine1 ?: current.location
        when (place) {
            PlaceChange.Keep -> Unit
            PlaceChange.Clear -> field("place", currentPlace, null)
            is PlaceChange.Set -> field("place", currentPlace, place.place.addressLine1)
        }
        recordPeriods(collected, current, activitySchedule, application)
    }

    // 모집·활동 기간을 서로 바꿔 넣은 저장(어드민 "모집 ↔ 활동 바꾸기")은 PERIOD_SWAP 하나로, 그 밖에는 바꾼 기간마다 원문 문구의 해석(DATE_CURATED)으로
    private fun recordPeriods(collected: CollectedActivityRef, current: Activity, activitySchedule: ScheduleChange, application: ScheduleChange) {
        fun newTime(change: ScheduleChange) = (change as? ScheduleChange.Set)?.edit?.toTime()
        // 바꿔 넣었는지는 날짜로만 본다(활동 기간은 종일, 모집 기간은 시각일 수 있다)
        fun days(time: ScheduleTime?) = when (time) {
            is ScheduleTime.AllDay -> time.start to time.endExclusive.minusDays(1)
            is ScheduleTime.Timed -> time.start?.toLocalDate() to time.end?.toLocalDate()
            null -> null
        }
        val oldApplication = current.application
        val oldActivity = current.activitySchedule
        val note = (activitySchedule as? ScheduleChange.Set)?.edit?.note ?: (application as? ScheduleChange.Set)?.edit?.note
        val swapped = activitySchedule is ScheduleChange.Set && application is ScheduleChange.Set && oldApplication != null &&
            days(oldApplication.time) != days(oldActivity.time) &&
            days(newTime(activitySchedule)) == days(oldApplication.time) && days(newTime(application)) == days(oldActivity.time)
        if (swapped) {
            feedback.record(
                NewFeedback(
                    FeedbackKind.PERIOD_SWAP, collected.programId, collected.host, "모집: ${oldApplication!!.dateLabel} / 활동: ${oldActivity.dateLabel}",
                    mapOf("application" to times(newTime(application)), "activity" to times(newTime(activitySchedule))), note, current.id,
                )
            )
            return
        }
        listOf(ScheduleSlot.ACTIVITY to (activitySchedule to oldActivity), ScheduleSlot.APPLICATION to (application to oldApplication)).forEach { (slot, pair) ->
            val (change, old) = pair
            if (change !is ScheduleChange.Set || old == null || old.dateLabel.isBlank()) return@forEach
            val time = change.edit.toTime() ?: return@forEach
            if (time == old.time) return@forEach
            feedback.record(NewFeedback(FeedbackKind.DATE_CURATED, collected.programId, collected.host, old.dateLabel, mapOf("slot" to slot.name) + times(time), change.edit.note, current.id))
        }
    }

    // 피드백의 날짜 값: 종일은 startDate·endDate(끝 포함), 시각은 startAt·endAt(ISO 8601 오프셋)
    private fun times(time: ScheduleTime?): Map<String, Any?> = when (time) {
        is ScheduleTime.AllDay -> mapOf("startDate" to time.start.toString(), "endDate" to time.endExclusive.minusDays(1).toString())
        is ScheduleTime.Timed -> mapOf("startAt" to time.start?.toOffsetDateTime()?.toString(), "endAt" to time.end?.toOffsetDateTime()?.toString()).filterValues { it != null }
        null -> emptyMap()
    }

    private fun applySchedule(activityId: Id, slot: ScheduleSlot, change: ScheduleChange) {
        when (change) {
            ScheduleChange.Keep -> Unit
            ScheduleChange.Clear -> curations.updateSchedule(activityId, slot, null)
            is ScheduleChange.Set -> curations.updateSchedule(activityId, slot, change.edit.toTime(), change.edit.note)
        }
    }

    // 슬롯 일정 행이 있어야 둘 수 있다(모집 기간이 없는데 값을 보내면 400). 반복이 남으면 그 행의 날짜·겹침 확인 시간과 맞아야 한다(틀리면 400, 트랜잭션째 되돌린다)
    private fun applyTiming(activityId: Id, activityTiming: TimingChange?, applicationTiming: TimingChange?, datesChanged: Boolean) {
        if (activityTiming == null && applicationTiming == null && !datesChanged) return
        val activity = activities.getById(activityId, publicationStatus = null).activity
        fun apply(schedule: Schedule, change: TimingChange?) {
            val overlap = (change ?: TimingChange()).overlapOf(schedule)
            val rule = (change ?: TimingChange()).recurrenceOf(schedule)
            rule?.requireFits(schedule.time, overlap)
            if (change != null) curations.updateTiming(activityId, schedule.id, overlap, rule)
        }
        apply(activity.activitySchedule, activityTiming)
        val application = activity.application
        if (application != null) apply(application, applicationTiming)
        else if (applicationTiming?.overlapCheck != null || applicationTiming?.recurrence != null) throw InvalidInputException("모집 기간이 없어 겹침 확인 시간·반복을 둘 수 없습니다")
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
        val lastFetched = settings.lastFetchedUrls(ids)
        val logoUpdatedAt = logos.updatedAtByProgram(ids)
        val today = LocalDate.now(SEOUL)
        return page.map {
            val collection = settingsById[it.id] ?: CollectionSettings.DEFAULT
            // 주소 토큰(admin-v1.md): 가장 최근 활동 이름의 기수 번호와, 지금 수집하면 볼 주소들을 함께 돌려준다
            val previousRound = CollectionUrlTemplate.previousRound(drafts.findPreviousEdition(it.id)?.name)
            ProgramListing(
                program = it,
                organization = organizationById[it.organizationId],
                settings = collection,
                configured = it.id in settingsById,
                lastSucceededOn = lastSucceeded[it.id],
                previousRound = previousRound,
                previewUrls = collection.officialUrl?.let { url -> CollectionUrlTemplate.expand(url, today, previousRound) }.orEmpty(),
                lastFetchedUrls = lastFetched[it.id].orEmpty(),
                logoUrl = logoUpdatedAt[it.id]?.let { at -> ProgramLogo.url(it.id, at) },
            )
        }
    }

    // officialUrl은 https, 허용 호스트는 스킴·경로·포트 없는 호스트 이름(소문자로 저장)
    private fun validate(collection: CollectionSettings): CollectionSettings {
        val url = collection.officialUrl?.trim()?.ifEmpty { null }
        // 주소 토큰(admin-v1.md): 모르는 {…}는 400, https 검사는 토큰을 샘플 값으로 바꾼 주소로 한다
        url?.let { CollectionUrlTemplate.unknownTokens(it).firstOrNull() }?.let { throw InvalidInputException("알 수 없는 주소 토큰입니다: {$it}") }
        val sample = url?.let(CollectionUrlTemplate::sample)
        if (sample != null && runCatching { URI(sample) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrEmpty() } != true) {
            throw InvalidInputException("공식 안내 페이지는 https 주소여야 합니다")
        }
        val hosts = collection.allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        hosts.firstOrNull { !HOST.matches(it) }?.let { throw InvalidInputException("허용 호스트는 도메인 이름만 씁니다: $it") }
        return collection.copy(officialUrl = url, allowedHosts = hosts)
    }

    // null이면 지금 값 그대로, 빈 문자열이면 지운다(선택 입력인 영문 값)
    // 보낸 태그 id가 모두 사전에 있어야 한다(없으면 400). 중복은 하나로
    private fun requireTags(ids: List<Id>): List<Id> {
        val wanted = ids.distinct()
        val found = tags.findAllById(wanted).map { it.id }.toSet()
        val missing = wanted.filterNot { it in found }
        if (missing.isNotEmpty()) throw InvalidInputException("없는 태그입니다: ${missing.joinToString(", ")}")
        return wanted
    }

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
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val MAX_STATUSES = 8
        val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    }
}
