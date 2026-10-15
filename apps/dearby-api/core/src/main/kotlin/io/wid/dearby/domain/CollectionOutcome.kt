package io.wid.dearby.domain

// 수집 한 번의 결과. SKIPPED: 같은 날 이미 수집했거나 설정이 수집할 수 없는 상태
enum class CollectionOutcome { SUCCEEDED, FAILED, SKIPPED }
