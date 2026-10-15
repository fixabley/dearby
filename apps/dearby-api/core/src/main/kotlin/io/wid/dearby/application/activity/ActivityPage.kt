package io.wid.dearby.application.activity

import io.wid.dearby.domain.Id

// nextCursor가 null이면 마지막 페이지
data class ActivityPage(val items: List<ActivityListing>, val nextCursor: Id?)
