package io.fixabley.dearby.widgets.notice.noticecard

internal data class NoticeCardState(val id: String, val title: String, val classification: String,
    val targetUser: String, val applicationSummary: String, val locationSummary: String,
    val hasIssues: Boolean, val organizationId: String?, val organizationName: String?, val saved: Boolean = false)
