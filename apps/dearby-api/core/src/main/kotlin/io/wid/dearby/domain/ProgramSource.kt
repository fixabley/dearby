package io.wid.dearby.domain

// 프로그램별 수집 설정. 수집은 allowedHosts에 정확히 일치하는 https 호스트만 검색·확인한다
data class ProgramSource(
    val id: Id,
    val programId: Id,
    val officialUrl: String, // 수집을 시작할 공식 안내 페이지
    val allowedHosts: List<String>,
    val enabled: Boolean,
)
