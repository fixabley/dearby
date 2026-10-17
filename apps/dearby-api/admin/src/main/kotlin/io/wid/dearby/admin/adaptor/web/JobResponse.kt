package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.JobListing

data class JobResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val parentId: Long?,
    val childCount: Int,
    val ancestors: List<ReferenceResponse>, // 최상위부터 바로 위까지. 최상위는 []
) {
    companion object {
        fun from(listing: JobListing) = with(listing) {
            JobResponse(job.id, job.name, job.nameEn, job.parentId, childCount, ancestors.map { (id, name) -> ReferenceResponse(id, name) })
        }
    }
}
