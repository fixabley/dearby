package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.adaptor.mcp.McpToolServer
import io.wid.dearby.batch.application.ActivityAgent
import io.wid.dearby.batch.application.AgentInput
import io.wid.dearby.batch.application.AgentLog
import io.wid.dearby.batch.application.AgentRun
import io.wid.dearby.batch.application.AgentTools
import io.wid.dearby.batch.application.ModelUsage
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// 수집 에이전트(2026-10-20 루프화). 이번 실행만의 도구 서버를 열고 Claude Code CLI가 그 도구로 페이지를 골라 받고 활동을 제출한다.
// 턴 한도는 CLI(--max-turns), 페이지 한도는 도구 서버가 지킨다. 턴 한도 밖의 CLI 오류는 던져 실행을 실패로 남긴다
@Component
class ClaudeCliActivityAgent(
    private val cli: ClaudeCli,
    private val properties: CollectionProperties,
    private val json: JsonMapper,
) : ActivityAgent {

    override fun run(input: AgentInput, tools: AgentTools): AgentRun {
        val toolbox = AgentToolbox(tools, properties.maxPages, json)
        val result = McpToolServer(toolbox, json).use { server -> cli.run(CollectionPrompts.AGENT_SYSTEM, CollectionPrompts.agentRequest(input), server.url) }
        val subtype = result.path("subtype").asString("")
        val maxTurns = subtype == "error_max_turns"
        if (result.path("is_error").asBoolean(false) && !maxTurns) {
            throw IllegalStateException("claude CLI failed: $subtype ${result.path("result").asString("").take(500)}")
        }
        val usage = usageOf(result.path("usage"))
        val stopReason = when {
            maxTurns -> "턴 한도(${properties.maxTurns})"
            toolbox.finished != null -> "finish: ${toolbox.finished}"
            else -> "모델이 finish 없이 끝냄"
        }
        val cost = result.path("total_cost_usd").takeIf { it.isNumber }?.asDouble()
        return AgentRun(usage, AgentLog(stopReason, maxTurns, result.path("num_turns").asInt(0), toolbox.pages, usage, cost, toolbox.calls))
    }

    private fun usageOf(usage: JsonNode) = ModelUsage(
        inputTokens = usage.path("input_tokens").asLong(0),
        outputTokens = usage.path("output_tokens").asLong(0),
        webSearchRequests = usage.path("server_tool_use").path("web_search_requests").asInt(0),
        cacheCreationInputTokens = usage.path("cache_creation_input_tokens").asLong(0),
        cacheReadInputTokens = usage.path("cache_read_input_tokens").asLong(0),
    )
}
