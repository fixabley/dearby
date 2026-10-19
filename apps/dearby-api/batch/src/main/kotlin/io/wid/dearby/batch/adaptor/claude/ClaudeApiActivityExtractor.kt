package io.wid.dearby.batch.adaptor.claude

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import io.wid.dearby.batch.application.ActivityExtractor
import io.wid.dearby.batch.application.Extraction
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.domain.collection.DateLabelExample
import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.batch.application.ModelUsage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// Anthropic API 방식. 직접 받은 페이지 본문을 넘겨 활동을 JSON으로 뽑는다(structured outputs). 웹 도구는 쓰지 않는다
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_API, matchIfMissing = true)
class ClaudeApiActivityExtractor(
    private val anthropic: AnthropicClientHolder,
    private val properties: CollectionProperties,
    private val json: JsonMapper,
) : ActivityExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(page: FetchedPage, programName: String, watchlist: List<WatchEntry>, previous: PreviousEdition?, examples: List<DateLabelExample>): Extraction {
        if (page.text.length > CollectionPrompts.MAX_PAGE_CHARS) log.warn("Page text truncated to {} chars: {}", CollectionPrompts.MAX_PAGE_CHARS, page.url)
        val params = MessageCreateParams.builder()
            .model(properties.model)
            .maxTokens(8192L)
            .system(CollectionPrompts.EXTRACT_SYSTEM)
            .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(SCHEMA).build()).build())
            .addUserMessage(CollectionPrompts.extractRequest(programName, page.url, page.text, watchlist, previous, examples))
            .build()
        val response = anthropic.client.messages().create(params)
        val text = response.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("")
        val usage = response.usage()
        return Extraction(
            activities = json.readValue<ExtractionResult>(text).activities,
            usage = ModelUsage(inputTokens = usage.inputTokens(), outputTokens = usage.outputTokens()),
        )
    }

    private companion object {
        val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder().apply {
            CollectionPrompts.EXTRACT_SCHEMA.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
        }.build()
    }
}
