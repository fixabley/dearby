package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import java.util.*

// PUT /v1/profile의 직무 태그. jobId와 jobName을 함께 보내면 jobId를 쓴다
data class JobInput(val id: UUID, val jobId: Id?, val jobName: String?)
