package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminActivityInfoService
import io.wid.dearby.application.activity.ActivitySearch
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
    fun updateProgram(@PathVariable id: Long, @RequestBody request: ProgramUpdateRequest) =
        ProgramResponse.from(activityInfo.updateProgram(id, request.name, request.nameEn, request.organizationId))

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

    @PatchMapping("/activities/{id}")
    fun updateActivityTexts(@PathVariable id: Long, @RequestBody request: ActivityTextsRequest) =
        AdminActivityResponse.from(activityInfo.updateActivityTexts(id, request.name, request.nameEn, request.description, request.descriptionEn))

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
