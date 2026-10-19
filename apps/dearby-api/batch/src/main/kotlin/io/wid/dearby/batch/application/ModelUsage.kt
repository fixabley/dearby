package io.wid.dearby.batch.application

// 모델 호출 사용량. collection_runs에 합쳐 남긴다. 캐시 토큰은 inputTokens와 따로 온다(프롬프트 캐싱)
data class ModelUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val webSearchRequests: Int = 0,
    val cacheCreationInputTokens: Long = 0,
    val cacheReadInputTokens: Long = 0,
) {
    // 캐시 여부와 상관없이 처리한 입력 토큰. collection_runs.input_tokens에 쓴다
    val totalInputTokens: Long get() = inputTokens + cacheCreationInputTokens + cacheReadInputTokens

    operator fun plus(other: ModelUsage) = ModelUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        webSearchRequests + other.webSearchRequests,
        cacheCreationInputTokens + other.cacheCreationInputTokens,
        cacheReadInputTokens + other.cacheReadInputTokens,
    )
}
