package io.wid.dearby.application.activity

import io.wid.dearby.domain.Id

// q는 활동 이름·설명에서 대소문자 없이 찾는다. after는 이전 페이지의 nextCursor(활동 id, 오름차순)
data class ActivitySearch(
    val q: String?,
    val organizationId: Id?,
    val programId: Id?,
    val after: Id?,
    val size: Int,
)
