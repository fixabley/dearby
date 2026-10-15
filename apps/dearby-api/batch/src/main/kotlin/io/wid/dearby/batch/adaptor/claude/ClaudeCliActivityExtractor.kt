package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.ActivityExtractor
import io.wid.dearby.batch.application.Extraction
import io.wid.dearby.batch.application.FetchedPage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.treeToValue

// Claude Code CLI 방식. 도구 없이 본문만 넘겨 JSON(--json-schema)으로 뽑는다
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_CLI)
class ClaudeCliActivityExtractor(private val cli: ClaudeCli, private val json: JsonMapper) : ActivityExtractor {

    override fun extract(page: FetchedPage, programName: String): Extraction {
        val result = cli.run(
            CollectionPrompts.EXTRACT_SYSTEM,
            CollectionPrompts.extractRequest(programName, page.url, page.text),
            CollectionPrompts.EXTRACT_SCHEMA,
            emptyList(),
        )
        return Extraction(json.treeToValue<ExtractionResult>(result.output).activities, result.usage)
    }
}
