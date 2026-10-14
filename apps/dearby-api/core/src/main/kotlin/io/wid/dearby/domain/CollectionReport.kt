package io.wid.dearby.domain

// 수집 한 번의 집계. collection_runs 한 행에 기록한다
data class CollectionReport(
    val outcome: CollectionOutcome,
    val foundCount: Int = 0,
    val savedCount: Int = 0,
    val rejectedReasons: List<String> = emptyList(),
    val error: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val webSearchRequests: Int = 0,
)
