package io.wid.dearby.adaptor.web.v1.activities

// nextCursor를 다음 요청의 cursor로 보낸다. null이면 마지막 페이지
data class ActivityPageResponse(val items: List<ActivityResponse>, val nextCursor: Long?)
