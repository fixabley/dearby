package io.wid.dearby.batch.adaptor.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import org.springframework.stereotype.Component

// API 키가 없어도 앱은 뜨고 수집만 건너뛰도록 클라이언트를 처음 쓸 때 만든다
@Component
class AnthropicClientHolder {

    val configured: Boolean get() = !System.getenv("ANTHROPIC_API_KEY").isNullOrBlank()

    val client: AnthropicClient by lazy { AnthropicOkHttpClient.fromEnv() }
}
