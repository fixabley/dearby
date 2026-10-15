package io.wid.dearby.admin.application

import io.wid.dearby.domain.Id

// nextCursor가 null이면 마지막 페이지
data class Page<T>(val items: List<T>, val nextCursor: Id?)
