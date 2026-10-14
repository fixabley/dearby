package io.wid.dearby.batch.adaptor.claude

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import io.wid.dearby.batch.application.ActivityExtractor
import io.wid.dearby.batch.application.Extraction
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.ModelUsage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// 직접 받은 페이지 본문을 넘겨 활동을 JSON으로 뽑는다(structured outputs). 웹 도구는 쓰지 않는다
@Component
class ClaudeActivityExtractor(
    private val anthropic: AnthropicClientHolder,
    private val properties: CollectionProperties,
    private val json: JsonMapper,
) : ActivityExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun extract(page: FetchedPage, programName: String): Extraction {
        if (page.text.length > MAX_PAGE_CHARS) log.warn("Page text truncated to {} chars: {}", MAX_PAGE_CHARS, page.url)
        val params = MessageCreateParams.builder()
            .model(properties.model)
            .maxTokens(8192L)
            .system(SYSTEM)
            .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(SCHEMA).build()).build())
            .addUserMessage("프로그램: $programName\n페이지: ${page.url}\n\n<page>\n${page.text.take(MAX_PAGE_CHARS)}\n</page>")
            .build()
        val response = anthropic.client.messages().create(params)
        val text = response.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("")
        val usage = response.usage()
        return Extraction(
            activities = json.readValue<Result>(text).activities,
            usage = ModelUsage(inputTokens = usage.inputTokens(), outputTokens = usage.outputTokens()),
        )
    }

    private data class Result(val activities: List<ExtractedActivity>)

    private companion object {
        const val MAX_PAGE_CHARS = 150_000

        val SYSTEM = """
            페이지 본문에서 이 프로그램의 활동(행사·교육·공모 등 신청할 수 있는 회차)을 뽑는다.
            - 본문에 적힌 내용만 쓴다. 추측하지 않는다. 모르는 값은 null로 둔다.
            - roundKey는 회차를 구분하는 짧은 식별자다(예: "2026-본행사", "2026-이력서피드백").
            - 시각은 본문에 시각까지 있을 때만 ISO 8601 오프셋 형식(예: 2026-10-08T19:00:00+09:00)으로 쓴다. 날짜만 있으면 시각은 null로 두고 dateLabel에 원문 문구를 쓴다.
            - evidenceQuote는 이 활동이 본문에 있다는 근거로, 본문에서 그대로 복사한 10~200자 연속 구절이다.
            - participationType: 신청하면 참가하면 REGISTRATION, 심사로 선발하면 SELECTION.
            - 활동이 없으면 activities를 빈 배열로 둔다.
        """.trimIndent()

        private fun nullable(type: String) = mapOf("type" to listOf(type, "null"))
        private fun obj(properties: Map<String, Any>) = mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to properties.keys.toList(),
            "additionalProperties" to false,
        )

        val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder().apply {
            obj(
                mapOf(
                    "activities" to mapOf(
                        "type" to "array",
                        "items" to obj(
                            mapOf(
                                "roundKey" to mapOf("type" to "string"),
                                "name" to mapOf("type" to "string"),
                                "description" to mapOf("type" to "string"),
                                "participationType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("REGISTRATION", "SELECTION", null)),
                                "applicationUrl" to nullable("string"),
                                "recruitmentStartAt" to nullable("string"),
                                "recruitmentEndAt" to nullable("string"),
                                "dateLabel" to mapOf("type" to "string"),
                                "schedules" to mapOf(
                                    "type" to "array",
                                    "items" to obj(
                                        mapOf(
                                            "title" to mapOf("type" to "string"),
                                            "startAt" to nullable("string"),
                                            "endAt" to nullable("string"),
                                            "dateLabel" to mapOf("type" to "string"),
                                        )
                                    ),
                                ),
                                "location" to nullable("string"),
                                "cost" to nullable("string"),
                                "audience" to nullable("string"),
                                "evidenceQuote" to mapOf("type" to "string"),
                            )
                        ),
                    ),
                )
            ).forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
        }.build()
    }
}
