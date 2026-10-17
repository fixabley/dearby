package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.profile.ProfileJobForReview
import java.util.*

// 직접 입력한 직무 태그와 작성자 프로필 이름(계정 id는 싣지 않는다)
data class JobEntryReviewResponse(val id: UUID, val profileName: String, val job: ReferenceResponse?, val jobName: String?, val approval: String, val approvalReason: String?) {
    companion object {
        fun from(r: ProfileJobForReview) = with(r.entry) {
            JobEntryReviewResponse(id, r.profileName, job?.let { ReferenceResponse(it.id, it.name) }, jobName, approval.name.lowercase(), approvalReason)
        }
    }
}
