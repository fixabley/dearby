package io.wid.dearby.admin.adaptor.web

import java.util.*

data class ReviewPageResponse<T>(val items: List<T>, val nextCursor: UUID?)
