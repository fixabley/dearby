package io.wid.dearby.adaptor.web.v1.profile

// 최신순. nextCursor(버전 번호)를 다음 요청의 cursor로 보낸다
data class VersionPageResponse(val items: List<VersionSummaryResponse>, val nextCursor: Int?)
