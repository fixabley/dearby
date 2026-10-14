package io.wid.dearby.batch.application

// 모델 호출 사용량. collection_runs에 합쳐 남긴다
data class ModelUsage(val inputTokens: Long = 0, val outputTokens: Long = 0, val webSearchRequests: Int = 0) {
    operator fun plus(other: ModelUsage) = ModelUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        webSearchRequests + other.webSearchRequests,
    )
}
