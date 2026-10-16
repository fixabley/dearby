package io.wid.dearby.admin.application

import java.util.*

// 이력 검토 목록 페이지. nextCursor(이력 id)가 null이면 마지막 페이지
data class ReviewPage<T>(val items: List<T>, val nextCursor: UUID?)
