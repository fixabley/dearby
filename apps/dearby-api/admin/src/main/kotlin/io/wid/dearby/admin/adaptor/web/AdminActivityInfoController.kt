package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.ActivityCreateInput
import io.wid.dearby.admin.application.AdminActivityInfoService
import io.wid.dearby.admin.application.CategoryChange
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.admin.application.EligibilityChange
import io.wid.dearby.admin.application.ScheduleChange
import io.wid.dearby.application.activity.ScheduleEdit
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.domain.PublicationStatus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// shared/contracts/admin-v1.md. 모든 경로는 ADMIN 역할만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin")
class AdminActivityInfoController(private val activityInfo: AdminActivityInfoService) {

    @GetMapping("/organizations")
    fun organizations(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = activityInfo.searchOrganizations(q, parentId, rootsOnly, cursor, size).let { page ->
        PageResponse(page.items.map(OrganizationResponse::from), page.nextCursor)
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrganization(@RequestBody request: OrganizationCreateRequest) =
        OrganizationResponse.from(activityInfo.createOrganization(request.name, request.nameEn, request.parentOrganizationId))

    @PatchMapping("/organizations/{id}")
    fun updateOrganization(@PathVariable id: Long, @RequestBody request: OrganizationUpdateRequest) =
        OrganizationResponse.from(activityInfo.updateOrganization(id, request.name, request.nameEn, request.parentOrganizationId, request.clearParent))

    @DeleteMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOrganization(@PathVariable id: Long) = activityInfo.deleteOrganization(id)

    @GetMapping("/programs")
    fun programs(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = activityInfo.searchPrograms(q, organizationId, cursor, size).let { page ->
        PageResponse(page.items.map(ProgramResponse::from), page.nextCursor)
    }

    @PostMapping("/programs")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProgram(@RequestBody request: ProgramCreateRequest) =
        ProgramResponse.from(activityInfo.createProgram(request.organizationId, request.name, request.nameEn, request.collection?.toSettings()))

    @PatchMapping("/programs/{id}")
    fun updateProgram(@PathVariable id: Long, @RequestBody request: ProgramUpdateRequest): ProgramResponse {
        // 태그(native-v1.md "태그"): 생략은 유지, 목록은 통째로 바꾼다. applyTagsToActivities면 운영자 미지정 활동에 합집합
        val result = activityInfo.updateProgram(
            id, request.name, request.nameEn, request.organizationId, categoryChange(request.category), request.applyCategoryToActivities,
            tagIds = request.tagIds, applyTagsToActivities = request.applyTagsToActivities,
        )
        return ProgramResponse.from(result.listing, result.appliedActivityCount, result.appliedTagActivityCount)
    }

    @DeleteMapping("/programs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProgram(@PathVariable id: Long) = activityInfo.deleteProgram(id)

    @GetMapping("/programs/{id}/collection-settings")
    fun collectionSettings(@PathVariable id: Long) = CollectionSettingsResponse.from(activityInfo.getCollectionSettings(id))

    @PutMapping("/programs/{id}/collection-settings")
    fun putCollectionSettings(@PathVariable id: Long, @RequestBody request: CollectionSettingsRequest) =
        CollectionSettingsResponse.from(activityInfo.putCollectionSettings(id, request.toSettings()))

    @GetMapping("/activities")
    fun activities(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) programId: Long?,
        @RequestParam(required = false) publicationStatus: PublicationStatus?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = activityInfo.searchActivities(ActivitySearch(q, organizationId, programId, cursor, size, publicationStatus)).let { page ->
        PageResponse(page.items.map(AdminActivityResponse::from), page.nextCursor)
    }

    // 운영자 직접 등록(admin-v1.md "POST /v1/admin/activities", 2026-10-19)
    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    fun createActivity(@RequestBody request: ActivityCreateRequest): AdminActivityResponse {
        val eligibility = request.eligibility?.takeUnless { it.isNull }
            ?.let { EligibilityJson.parse(it, lenient = false) ?: throw InvalidInputException("대상자 조건이 비어 있습니다") }
        val participationType = request.participationType?.let { value ->
            ParticipationType.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw InvalidInputException("participationType은 REGISTRATION 또는 SELECTION입니다")
        }
        val activitySchedule = request.activitySchedule?.also { if (!it.isObject) throw InvalidInputException("활동 기간은 객체여야 합니다") }
        return AdminActivityResponse.from(
            activityInfo.createActivity(
                ActivityCreateInput(
                    programId = request.programId ?: throw InvalidInputException("programId가 필요합니다"),
                    name = request.name.orEmpty(), nameEn = request.nameEn, description = request.description.orEmpty(), descriptionEn = request.descriptionEn,
                    officialUrl = request.officialUrl.orEmpty(), applicationUrl = request.applicationUrl,
                    participationType = participationType, category = request.category?.trim()?.ifEmpty { null }?.let(ActivityCategory::parse),
                    activitySchedule = activitySchedule?.let(::scheduleEdit), activityDateLabel = activitySchedule?.text("dateLabel"),
                    application = request.application?.let { node -> if (node.isNull) null else scheduleEdit(node) },
                    eligibility = eligibility, audience = request.audience, location = request.location, cost = request.cost,
                    tagIds = request.tagIds,
                )
            )
        )
    }

    @PatchMapping("/activities/{id}")
    fun updateActivityTexts(@PathVariable id: Long, @RequestBody request: ActivityTextsRequest): AdminActivityResponse {
        // 대상자 조건: 생략은 유지, JSON null은 삭제, 객체는 어휘 검증(틀리면 400) 뒤 교체
        val eligibility = when {
            request.eligibility == null -> EligibilityChange.Keep
            request.eligibility.isNull -> EligibilityChange.Clear
            else -> EligibilityChange.Set(EligibilityJson.parse(request.eligibility, lenient = false) ?: throw InvalidInputException("대상자 조건이 비어 있습니다"))
        }
        return AdminActivityResponse.from(
            activityInfo.updateActivityTexts(
                id, request.name, request.nameEn, request.description, request.descriptionEn, eligibility,
                activitySchedule = scheduleChange(request.activitySchedule), application = scheduleChange(request.application),
                officialUrl = request.officialUrl, applicationUrl = request.applicationUrl,
                category = categoryChange(request.category), tagIds = request.tagIds,
            )
        )
    }

    // 활동 종류(native-v1.md "활동 종류"): 생략은 유지, JSON null은 해제, 코드는 지정(모르는 코드 400)
    private fun categoryChange(node: JsonNode?): CategoryChange = when {
        node == null -> CategoryChange.Keep
        node.isNull -> CategoryChange.Clear
        else -> CategoryChange.Set(ActivityCategory.parse(node.asString()))
    }

    // 일정 직접 지정(admin-v1.md): {startDate?, endDate?, startAt?, endAt?} 또는 null. 날짜는 yyyy-MM-dd, 시각은 ISO 8601 오프셋
    private fun scheduleChange(node: JsonNode?): ScheduleChange = when {
        node == null -> ScheduleChange.Keep
        node.isNull -> ScheduleChange.Clear
        !node.isObject -> throw InvalidInputException("일정은 객체 또는 null이어야 합니다")
        else -> ScheduleChange.Set(scheduleEdit(node))
    }

    private fun scheduleEdit(node: JsonNode): ScheduleEdit {
        if (!node.isObject) throw InvalidInputException("일정은 객체여야 합니다")
        return ScheduleEdit(
            startDate = node.text("startDate")?.let(::date),
            endDate = node.text("endDate")?.let(::date),
            startAt = node.text("startAt")?.let(::dateTime),
            endAt = node.text("endAt")?.let(::dateTime),
            note = node.text("note")?.also { if (it.length > 300) throw InvalidInputException("사유는 300자까지입니다") },
        )
    }

    private fun JsonNode.text(field: String): String? = get(field)?.takeUnless { it.isNull }?.asString()?.trim()?.ifEmpty { null }
    private fun date(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("날짜 형식이 올바르지 않습니다: $value") }
    private fun dateTime(value: String): OffsetDateTime = try { OffsetDateTime.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("시각 형식이 올바르지 않습니다: $value") }

    @DeleteMapping("/activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteActivity(@PathVariable id: Long) = activityInfo.deleteActivity(id)

    @PatchMapping("/activities/{id}/publication-status")
    fun setPublicationStatus(@PathVariable id: Long, @RequestBody request: PublicationStatusRequest) =
        AdminActivityResponse.from(activityInfo.setPublicationStatus(id, request.publicationStatus))

    private companion object {
        const val DEFAULT_SIZE = ActivityService.DEFAULT_PAGE_SIZE.toString()
    }
}
