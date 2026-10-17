package io.wid.dearby.admin.adaptor.web

// decision: approve(jobId로 직무를 지정할 수 있다) 또는 reject(reason 필수)
data class JobApprovalRequest(val decision: String, val jobId: Long? = null, val reason: String? = null)
