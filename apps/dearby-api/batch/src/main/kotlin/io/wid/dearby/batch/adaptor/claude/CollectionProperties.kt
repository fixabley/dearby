package io.wid.dearby.batch.adaptor.claude

import org.springframework.boot.context.properties.ConfigurationProperties

// 수집 모델. API 키는 ANTHROPIC_API_KEY 환경변수(Anthropic SDK가 직접 읽는다)
@ConfigurationProperties("dearby.collection")
data class CollectionProperties(
    val model: String = "claude-haiku-4-5",
)
