package io.wid.dearby.admin.adaptor.web

// nextCursor를 다음 요청의 cursor로 보낸다. null이면 마지막 페이지
data class PageResponse<T>(val items: List<T>, val nextCursor: Long?)
