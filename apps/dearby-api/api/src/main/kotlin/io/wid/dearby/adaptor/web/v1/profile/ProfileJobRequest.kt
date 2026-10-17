package io.wid.dearby.adaptor.web.v1.profile

import java.util.*

// 직무 태그. jobId와 jobName을 함께 보내면 jobId를 쓴다. id는 클라이언트가 만든 UUID(저장해도 승인 상태를 지킨다)
data class ProfileJobRequest(val id: UUID, val jobId: Long? = null, val jobName: String? = null)
