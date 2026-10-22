package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.ProgressStatus

// PUT /v1/admin/activities/:id/schedules/:scheduleId/statuses. id가 없으면 새 상태
data class ScheduleStatusesRequest(val items: List<ScheduleStatusItem> = emptyList())

data class ScheduleStatusItem(val id: Long? = null, val name: String? = null, val kind: String? = null) {
    fun toStatus() = ProgressStatus(
        id = id,
        name = name.orEmpty(),
        kind = ProgressKind.entries.firstOrNull { it.name.equals(kind?.trim(), ignoreCase = true) }
            ?: throw InvalidInputException("kind는 WAITING·PASSED·FAILED·OTHER 중 하나입니다"),
    )
}

data class ScheduleStatusesResponse(val items: List<ProgressStatusResponse>)
