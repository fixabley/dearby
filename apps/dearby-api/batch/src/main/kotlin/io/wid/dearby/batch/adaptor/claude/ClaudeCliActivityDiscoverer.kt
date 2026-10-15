package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.ActivityDiscoverer
import io.wid.dearby.batch.application.Discovery
import io.wid.dearby.domain.CollectionTarget
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.treeToValue

// Claude Code CLI 방식. 내장 WebSearch만 허용하고, 찾은 주소 중 https(허용 호스트가 있으면 그 안의 것)만 쓴다(검색 범위는 지시문으로만 좁혀진다)
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_CLI)
class ClaudeCliActivityDiscoverer(private val cli: ClaudeCli, private val json: JsonMapper) : ActivityDiscoverer {

    override fun discover(target: CollectionTarget): Discovery {
        val result = cli.run(
            CollectionPrompts.DISCOVER_SYSTEM,
            CollectionPrompts.discoverRequest(target),
            CollectionPrompts.DISCOVER_SCHEMA,
            listOf("WebSearch"),
        )
        val urls = CollectionPrompts.usableUrls(json.treeToValue<DiscoveryResult>(result.output).urls, target)
        return Discovery(urls, result.usage)
    }
}
