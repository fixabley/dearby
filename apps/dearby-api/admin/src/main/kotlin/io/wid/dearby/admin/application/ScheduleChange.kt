package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ScheduleEdit

// PATCH /v1/admin/activities/:id의 activitySchedule·application: 보내지 않음(Keep) · JSON null(Clear) · 객체(Set)
sealed interface ScheduleChange {
    data object Keep : ScheduleChange
    data object Clear : ScheduleChange
    data class Set(val edit: ScheduleEdit) : ScheduleChange
}
