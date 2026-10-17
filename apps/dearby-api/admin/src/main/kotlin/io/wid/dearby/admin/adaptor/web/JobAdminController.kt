package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.JobAdminService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 직무 활동 정보(admin-v1.md "직무"). ADMIN만
@RestController
@RequestMapping("/v1/admin/jobs")
class JobAdminController(private val jobs: JobAdminService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ) = jobs.search(q, parentId, rootsOnly, cursor, size).let { page -> PageResponse(page.items.map(JobResponse::from), page.nextCursor) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: JobCreateRequest) = JobResponse.from(jobs.create(request.name, request.nameEn, request.parentId))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: JobUpdateRequest) =
        JobResponse.from(jobs.update(id, request.name, request.nameEn, request.parentId, request.clearParent))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = jobs.delete(id)
}
