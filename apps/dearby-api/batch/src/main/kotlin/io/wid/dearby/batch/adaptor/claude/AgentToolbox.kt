package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.AgentToolCall
import io.wid.dearby.batch.application.AgentTools
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.FeedbackKind
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// 수집 에이전트의 도구(fetch_page·render_page·submit_candidate·search_feedback·finish) 처리. 프로그램 하나의 수집마다 새로 만든다.
// 받기 실패·거절은 오류 결과(isError)로 돌려주고 던지지 않는다. 페이지 한도는 여기서 지킨다(모델이 지시를 어겨도)
class AgentToolbox(private val tools: AgentTools, private val programId: Id, private val maxPages: Int, private val json: JsonMapper) {

    data class Result(val text: String, val isError: Boolean)

    val calls = mutableListOf<AgentToolCall>()
    var pages = 0
        private set
    var finished: String? = null // finish를 부르면 그 이유
        private set

    @Synchronized
    fun call(name: String, arguments: JsonNode): Result = when (name) {
        FETCH, RENDER -> {
            val url = text(arguments, "url")
            val result = when {
                url == null -> Result("url이 없음", true)
                pages >= maxPages -> Result("페이지 한도($maxPages)에 닿아 더 받을 수 없음. 받은 페이지로 제출하고 finish하라", true)
                else -> {
                    pages++
                    try {
                        Result(CollectionPrompts.pageResult(url, if (name == RENDER) tools.render(url) else tools.fetch(url)), false)
                    } catch (e: PageFetchException) {
                        Result("받을 수 없음: ${e.message}", true)
                    }
                }
            }
            calls += AgentToolCall(name, url, if (result.isError) result.text else "받음")
            result
        }
        SUBMIT -> {
            val sourceUrl = text(arguments, "sourceUrl")
            val candidate = runCatching { json.treeToValue(arguments, ExtractedActivity::class.java) }.getOrNull()
            val result = if (candidate == null || sourceUrl == null) {
                Result("입력이 활동 스키마와 맞지 않음(필수 필드·sourceUrl 확인)", true)
            } else {
                tools.submit(candidate, sourceUrl).let { verdict ->
                    Result(((if (verdict.saved) "저장됨: " else "거절됨: ") + verdict.message + verdict.warnings.joinToString("") { "\n$it" }), !verdict.saved)
                }
            }
            calls += AgentToolCall(SUBMIT, sourceUrl, result.text)
            result
        }
        SEARCH_FEEDBACK -> {
            val query = text(arguments, "text")
            val kind = text(arguments, "kind")?.let { name -> FeedbackKind.entries.firstOrNull { it.name == name } }
            val result = if (query == null) {
                Result("text가 없음", true)
            } else {
                val rows = tools.searchFeedback(query, kind)
                Result(if (rows.isEmpty()) "비슷한 운영자 피드백 없음" else rows.joinToString("\n") { "- " + CollectionPrompts.feedbackLine(it, programId) }, false)
            }
            calls += AgentToolCall(SEARCH_FEEDBACK, null, if (result.isError) result.text else "찾음: ${result.text.lines().count { it.startsWith("- ") }}건")
            result
        }
        FINISH -> {
            val reason = text(arguments, "reason") ?: ""
            finished = reason
            calls += AgentToolCall(FINISH, null, reason)
            Result("끝", false)
        }
        else -> Result("모르는 도구: $name", true)
    }

    private fun text(arguments: JsonNode, key: String): String? =
        arguments.path(key).takeIf { it.isString }?.asString()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val FETCH = "fetch_page"
        const val RENDER = "render_page"
        const val SUBMIT = "submit_candidate"
        const val SEARCH_FEEDBACK = "search_feedback"
        const val FINISH = "finish"

        // MCP tools/list 형태(name·description·inputSchema)
        val DEFINITIONS: List<Map<String, Any>> = listOf(
            definition(FETCH, "페이지를 직접 받아 본문(태그를 뺀 텍스트)과 링크 목록을 돌려준다. https, 허용 도메인 안의 주소만.", mapOf("url" to mapOf("type" to "string"))),
            definition(RENDER, "자바스크립트로 그리는 페이지를 헤드리스 브라우저로 실행한 뒤 본문과 링크를 돌려준다. fetch_page 본문이 비었거나 아주 짧을 때만.", mapOf("url" to mapOf("type" to "string"))),
            mapOf(
                "name" to SUBMIT,
                "description" to "찾은 활동(기수·회차 하나)을 제출한다. 서버가 sourceUrl 원문과 대조해 저장하거나 거절 이유를 돌려준다.",
                "inputSchema" to CollectionPrompts.SUBMIT_SCHEMA,
            ),
            mapOf(
                "name" to SEARCH_FEEDBACK,
                "description" to "운영자가 어드민에서 바로잡은 기록 중 text(날짜 문구·활동 이름·수집 값)와 비슷한 것을 최대 8건 찾는다. 같은 프로그램 → 같은 호스트 → 다른 프로그램 순.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("text" to mapOf("type" to "string"), "kind" to mapOf("type" to "string", "enum" to FeedbackKind.entries.map { it.name })),
                    "required" to listOf("text"),
                ),
            ),
            definition(FINISH, "수집을 끝낸다.", mapOf("reason" to mapOf("type" to "string"))),
        )

        private fun definition(name: String, description: String, properties: Map<String, Any>) = mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf("type" to "object", "properties" to properties, "required" to properties.keys.toList()),
        )
    }
}
