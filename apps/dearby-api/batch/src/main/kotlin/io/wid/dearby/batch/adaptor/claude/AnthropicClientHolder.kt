package io.wid.dearby.batch.adaptor.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import io.wid.dearby.batch.application.CollectionReadiness
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

// API 키가 없어도 앱은 뜨고 수집만 건너뛰도록 클라이언트를 처음 쓸 때 만든다
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_API, matchIfMissing = true)
class AnthropicClientHolder : CollectionReadiness {

    val client: AnthropicClient by lazy { AnthropicOkHttpClient.fromEnv() }

    override fun problem(): String? =
        if (System.getenv("ANTHROPIC_API_KEY").isNullOrBlank()) "ANTHROPIC_API_KEY is not set" else null
}
