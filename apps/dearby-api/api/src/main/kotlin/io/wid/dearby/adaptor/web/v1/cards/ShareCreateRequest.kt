package io.wid.dearby.adaptor.web.v1.cards

// 공유할 때 고른 게시된 활동 id(숫자), 0~10개
data class ShareCreateRequest(val activityIds: List<Long> = emptyList())
