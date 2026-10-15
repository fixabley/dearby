package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminCatalogService
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.domain.PublicationStatus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// shared/contracts/admin-v1.md. 모든 경로는 ADMIN 역할만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin")
class AdminCatalogController(private val catalog: AdminCatalogService) {

    @GetMapping("/organizations")
    fun organizations(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = catalog.searchOrganizations(q, parentId, rootsOnly, cursor, size).let { page ->
        PageResponse(page.items.map(OrganizationResponse::from), page.nextCursor)
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrganization(@RequestBody request: OrganizationCreateRequest) =
        OrganizationResponse.from(catalog.createOrganization(request.name, request.parentOrganizationId))

    @PatchMapping("/organizations/{id}")
    fun updateOrganization(@PathVariable id: Long, @RequestBody request: OrganizationUpdateRequest) =
        OrganizationResponse.from(catalog.updateOrganization(id, request.name, request.parentOrganizationId, request.clearParent))

    @DeleteMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOrganization(@PathVariable id: Long) = catalog.deleteOrganization(id)

    @GetMapping("/programs")
    fun programs(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = catalog.searchPrograms(q, organizationId, cursor, size).let { page ->
        PageResponse(page.items.map(ProgramResponse::from), page.nextCursor)
    }

    @PostMapping("/programs")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProgram(@RequestBody request: ProgramCreateRequest) =
        ProgramResponse.from(catalog.createProgram(request.organizationId, request.name, request.collection?.toSettings()))

    @PatchMapping("/programs/{id}")
    fun updateProgram(@PathVariable id: Long, @RequestBody request: ProgramUpdateRequest) =
        ProgramResponse.from(catalog.updateProgram(id, request.name, request.organizationId))

    @DeleteMapping("/programs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProgram(@PathVariable id: Long) = catalog.deleteProgram(id)

    @GetMapping("/programs/{id}/collection-settings")
    fun collectionSettings(@PathVariable id: Long) = CollectionSettingsResponse.from(catalog.getCollectionSettings(id))

    @PutMapping("/programs/{id}/collection-settings")
    fun putCollectionSettings(@PathVariable id: Long, @RequestBody request: CollectionSettingsRequest) =
        CollectionSettingsResponse.from(catalog.putCollectionSettings(id, request.toSettings()))

    @GetMapping("/activities")
    fun activities(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) organizationId: Long?,
        @RequestParam(required = false) programId: Long?,
        @RequestParam(required = false) publicationStatus: PublicationStatus?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ) = catalog.searchActivities(ActivitySearch(q, organizationId, programId, cursor, size, publicationStatus)).let { page ->
        PageResponse(page.items.map(AdminActivityResponse::from), page.nextCursor)
    }

    @DeleteMapping("/activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteActivity(@PathVariable id: Long) = catalog.deleteActivity(id)

    @PatchMapping("/activities/{id}/publication-status")
    fun setPublicationStatus(@PathVariable id: Long, @RequestBody request: PublicationStatusRequest) =
        AdminActivityResponse.from(catalog.setPublicationStatus(id, request.publicationStatus))

    private companion object {
        const val DEFAULT_SIZE = ActivityService.DEFAULT_PAGE_SIZE.toString()
    }
}
