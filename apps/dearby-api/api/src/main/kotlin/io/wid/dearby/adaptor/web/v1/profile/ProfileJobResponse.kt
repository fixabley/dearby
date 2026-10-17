package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.ProfileJob
import java.util.*

// native-v1.md "직무". 직무에서 골랐으면 job, 직접 입력이면 job이 null이고 approval이 pending/rejected일 수 있다
data class ProfileJobResponse(val id: UUID, val job: ActivityInfoRefResponse?, val jobName: String?, val approval: String, val approvalReason: String?) {
    companion object {
        fun from(j: ProfileJob) = ProfileJobResponse(j.id, j.job?.let(ActivityInfoRefResponse::from), j.jobName, j.approval.name.lowercase(), j.approvalReason)
    }
}
