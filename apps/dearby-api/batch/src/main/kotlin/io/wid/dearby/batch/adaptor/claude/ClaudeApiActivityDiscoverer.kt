package io.wid.dearby.batch.adaptor.claude

import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.WebSearchTool20250305
import io.wid.dearby.batch.application.ActivityDiscoverer
import io.wid.dearby.batch.application.Discovery
import io.wid.dearby.batch.application.ModelUsage
import io.wid.dearby.domain.ProgramSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI

// Anthropic API 방식. Claude 웹 검색(허용 호스트로 제한)으로 모집 공지 후보 페이지를 찾는다. 결과 URL은 검색 결과 블록에서 직접 읽는다
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_API, matchIfMissing = true)
class ClaudeApiActivityDiscoverer(
    private val anthropic: AnthropicClientHolder,
    private val properties: CollectionProperties,
) : ActivityDiscoverer {

    override fun discover(source: ProgramSource, programName: String): Discovery {
        val params = MessageCreateParams.builder()
            .model(properties.model)
            .maxTokens(2048L)
            .system(CollectionPrompts.DISCOVER_SYSTEM)
            // Haiku 4.5는 동적 필터링이 없는 기본 웹 검색 도구를 쓴다
            .addTool(WebSearchTool20250305.builder().maxUses(MAX_SEARCHES).allowedDomains(source.allowedHosts).build())
            .addUserMessage(CollectionPrompts.discoverRequest(source, programName))
            .build()
        val response = anthropic.client.messages().create(params)
        val urls = response.content()
            .flatMap { block -> block.webSearchToolResult().map { listOf(it) }.orElse(emptyList()) }
            .flatMap { result -> result.content().resultBlocks().orElse(emptyList()) }
            .map { it.url() }
            .filter { url -> runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && it.host?.lowercase() in source.allowedHosts } == true }
            .distinct()
        val usage = response.usage()
        return Discovery(
            urls = urls,
            usage = ModelUsage(
                inputTokens = usage.inputTokens(),
                outputTokens = usage.outputTokens(),
                webSearchRequests = usage.serverToolUse().map { it.webSearchRequests().toInt() }.orElse(0),
            ),
        )
    }

    private companion object {
        const val MAX_SEARCHES = 3L
    }
}
