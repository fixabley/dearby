package io.wid.dearby.domain.collection

import io.wid.dearby.domain.Id

// 오늘 수집할 프로그램과 그 수집 설정. 설정 행이 없는 프로그램은 기본값(웹 전체 검색, 매일)이다
data class CollectionTarget(
    val programId: Id,
    val programName: String,
    val organizationName: String?, // 웹 전체 검색어에 함께 쓴다
    val officialUrl: String?, // 수집을 시작할 공식 안내 페이지. 없으면 검색 결과만 쓴다
    val allowedHosts: List<String>, // 비어 있으면 웹 전체 검색, 있으면 이 호스트 안에서만
    val intervalDays: Int,
) {
    // 공식 도메인 안에서만 수집하는지
    val officialOnly: Boolean get() = allowedHosts.isNotEmpty()
}
