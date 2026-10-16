package io.wid.dearby.domain.curation

// 프로그램별 수집 설정(program_collection_settings). 행이 없으면 DEFAULT
data class CollectionSettings(
    val officialUrl: String?, // 수집을 시작할 공식 안내 페이지(https)
    val allowedHosts: List<String>, // 비어 있으면 웹 전체 검색
    val intervalDays: Int, // 성공한 수집 뒤 이 일수 동안은 다시 수집하지 않는다
    val enabled: Boolean,
) {
    init {
        require(intervalDays >= 1) { "수집 주기는 1일 이상이어야 합니다" }
    }

    val officialOnly: Boolean get() = allowedHosts.isNotEmpty()

    companion object {
        val DEFAULT = CollectionSettings(officialUrl = null, allowedHosts = emptyList(), intervalDays = 1, enabled = true)
    }
}
