package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.collection.CollectionFeedback
import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.collection.WatchKind
import io.wid.dearby.batch.application.AgentInput
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.domain.HighlightIcon
import io.wid.dearby.domain.Tag
import io.wid.dearby.domain.TagKind

// 수집 에이전트의 실행마다 바뀌는 요청과 submit_candidate 입력 스키마. 바뀌지 않는 작업 절차는 resources/collector/WORKFLOW.md
object CollectionPrompts {

    // 도구 결과로 보이는 본문 길이. Claude Code는 MCP 도구 결과를 기본 25,000토큰에서 자르고, 받은 페이지는 루프가 끝날 때까지 대화에 남는다.
    // 원문 대조(ActivityVerifier)는 자르지 않은 본문으로 한다
    const val MAX_PAGE_CHARS = 40_000
    private const val MAX_LINKS = 200

    // 프로그램·시작 주소·허용 도메인·한도와 힌트(태그 사전, 운영자 피드백 최근 기록, 이전 기수, 조심 목록)
    fun agentRequest(input: AgentInput, maxPages: Int, maxTurns: Int) = buildString {
        val target = input.target
        append("프로그램: ${target.programName}")
        target.organizationName?.let { append("\n주최 기관: $it") }
        if (target.officialUrls.isNotEmpty()) append("\n시작 주소: ${target.officialUrls.joinToString(", ")}")
        if (target.officialOnly) append("\n허용 도메인: ${target.allowedHosts.joinToString(", ")}")
        append("\n한도: 페이지 ${maxPages}쪽, 차례 ${maxTurns}번")
        // 태그 사전(native-v1.md "태그", "분야·성격 태그와 아이콘"): 종류별로 나눠 이 이름 중에서만 고른다. 사전 밖 이름은 저장할 때 버린다
        fieldDictionary(input.tags).takeIf { it.isNotEmpty() }?.let { append("\n분야 태그 사전(fieldTags는 이 중에서만, 가장 구체적인 분야로): $it") }
        input.tags.filter { it.kind == TagKind.TRAIT }.takeIf { it.isNotEmpty() }?.let { append("\n성격 태그 사전(tags는 이 중에서만 고른다): ${it.joinToString(", ") { tag -> tag.name }}") }
        // 운영자 피드백(collection_feedback): 이 프로그램의 최근 기록. 더 필요하면 search_feedback으로 찾는다
        input.feedback.takeIf { it.isNotEmpty() }?.let { rows ->
            append("\n운영자 피드백(이 프로그램의 최근 기록):")
            rows.forEach { append("\n- ").append(feedbackLine(it)) }
        }
        // 이전 기수(admin-v1.md "이전 기수 정보 자동완성"): 본문에 같은 내용이 있을 때 같은 어휘로 쓰게 하는 힌트. 서버가 비어 있는 필드를 따로 채운다
        input.previous?.takeIf { it.eligibility != null || it.audience != null }?.let { edition ->
            append("\n참고(이전 기수 \"${edition.name}\"):")
            edition.audience?.let { append("\n- 대상 원문: $it") }
            edition.eligibility?.let { append("\n- 대상자 조건: ${EligibilityJson.write(it)}") }
        }
        // 조심 목록(admin-v1.md)은 힌트로만 넘긴다. 저장 전 이름 대조가 보장이다
        input.watchlist.filter { it.kind == WatchKind.EXCLUDE }.takeIf { it.isNotEmpty() }?.let { excluded ->
            append("\n활동으로 만들지 않을 이름: ${excluded.joinToString(", ") { it.name }}")
        }
        input.watchlist.filter { it.kind == WatchKind.ALIAS && it.canonicalActivityName != null }.takeIf { it.isNotEmpty() }?.let { aliases ->
            append("\n이미 있는 활동의 다른 표기: ${aliases.joinToString(", ") { "${it.name} → ${it.canonicalActivityName}" }}")
        }
    }

    // 상위 분야 아래 하위 분야를 붙인다: "IT > 데이터·AI, 클라우드; 봉사"
    fun fieldDictionary(tags: List<Tag>): String {
        val fields = tags.filter { it.kind == TagKind.FIELD }
        val children = fields.filter { it.parentId != null }.groupBy { it.parentId }
        return fields.filter { it.parentId == null }.joinToString("; ") { top ->
            children[top.id]?.let { "${top.name} > ${it.joinToString(", ") { child -> child.name }}" } ?: top.name
        }
    }

    // `#12 [DATE_CURATED] "2026년 11월 1일~15일" → {"startDate":…} (이유: …)`. 다른 프로그램의 기록은 프로그램 이름을 붙인다
    fun feedbackLine(feedback: CollectionFeedback, programId: Long? = null): String {
        val program = feedback.programName?.takeIf { feedback.programId != programId && programId != null }?.let { " ($it)" } ?: ""
        val reason = feedback.reason?.let { " (이유: $it)" } ?: ""
        return "#${feedback.id} [${feedback.kind}]$program \"${feedback.sourceText}\" → ${feedback.correctedValue}$reason"
    }

    // 에이전트가 받은 페이지를 모델에게 보이는 형태: 자른 본문 + 따라갈 링크
    fun pageResult(url: String, page: FetchedPage) = buildString {
        append("주소: $url")
        append("\n\n<page>\n${page.text.take(MAX_PAGE_CHARS)}\n</page>")
        if (page.links.isNotEmpty()) append("\n\n링크:\n${page.links.take(MAX_LINKS).joinToString("\n")}")
    }

    // submit_candidate 도구 입력: 활동 하나 + 근거 페이지 주소
    val SUBMIT_SCHEMA: Map<String, Any> = obj(
        mapOf(
            "roundKey" to string(),
            "name" to string(),
            "nameEn" to nullableString(),
            "description" to string(),
            "descriptionEn" to nullableString(),
            "participationType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("REGISTRATION", "SELECTION", null)),
            "category" to mapOf("type" to listOf("string", "null"), "enum" to listOf("BOOTCAMP", "CONFERENCE", "WORKSHOP", "STUDY", "CLUB", "LECTURE", "HACKATHON", "CONTEST", "MEETUP", "OTHER", null)),
            "applicationUrl" to nullableString(),
            "recruitmentStartAt" to nullableString(),
            "recruitmentEndAt" to nullableString(),
            "recruitmentStartDate" to nullableString(),
            "recruitmentEndDate" to nullableString(),
            "dateLabel" to string(),
            "startDate" to nullableString(),
            "endDate" to nullableString(),
            "schedules" to mapOf(
                "type" to "array",
                "items" to obj(
                    mapOf(
                        "title" to string(),
                        "kind" to mapOf("type" to listOf("string", "null"), "enum" to listOf("DEADLINE", "ANNOUNCEMENT", "INTERVIEW", "EVENT", null)),
                        "startAt" to nullableString(),
                        "endAt" to nullableString(),
                        "startDate" to nullableString(),
                        "endDate" to nullableString(),
                        "dateLabel" to string(),
                        "recurrence" to nullableString(),
                        "timetable" to timetable(),
                    )
                ),
            ),
            "activityRecurrence" to mapOf(
                "type" to listOf("object", "null"),
                "properties" to mapOf("rule" to string(), "startTime" to string(), "endTime" to string()),
                "required" to listOf("rule", "startTime", "endTime"),
                "additionalProperties" to false,
            ),
            "location" to nullableString(),
            "place" to mapOf(
                "type" to listOf("object", "null"),
                "properties" to mapOf("name" to nullableString(), "address" to nullableString(), "online" to mapOf("type" to "boolean"), "evidenceQuote" to nullableString()),
                "required" to listOf("name", "address", "online", "evidenceQuote"),
                "additionalProperties" to false,
            ),
            "cost" to nullableString(),
            "audience" to nullableString(),
            "hook" to nullableString(),
            "highlights" to mapOf(
                "type" to "array",
                "items" to obj(mapOf("icon" to mapOf("type" to "string", "enum" to HighlightIcon.entries.map { it.code }), "text" to string())),
            ),
            "tags" to mapOf("type" to "array", "items" to string()),
            "fieldTags" to mapOf(
                "type" to "array",
                "items" to obj(mapOf("name" to string(), "relevance" to mapOf("type" to "integer"))),
            ),
            "eligibility" to eligibilityGroup(depth = 1),
            "evidenceQuote" to string(),
            "sourceUrl" to string(),
        )
    )

    // 대상자 조건 트리(native-v1.md "대상자 조건"). 재귀 스키마 대신 3단계까지 펼쳐 쓴다. 각 단계의 항목은 조건이거나(items null) 그룹이다(items 배열)
    private fun eligibilityGroup(depth: Int): Map<String, Any> = mapOf(
        "type" to listOf("object", "null"),
        "properties" to mapOf(
            "op" to mapOf("type" to "string", "enum" to listOf("all", "any")),
            "items" to mapOf("type" to "array", "items" to eligibilityItem(depth)),
        ),
        "required" to listOf("op", "items"),
        "additionalProperties" to false,
    )

    private fun eligibilityItem(depth: Int): Map<String, Any> {
        val condition = mapOf(
            "op" to mapOf("type" to listOf("string", "null"), "enum" to listOf("all", "any", null)),
            "items" to if (depth < 3) mapOf("type" to listOf("array", "null"), "items" to eligibilityItem(depth + 1)) else mapOf("type" to "null"),
            "field" to nullableString(),
            "fieldEn" to nullableString(),
            "from" to mapOf("type" to listOf("number", "null")),
            "to" to mapOf("type" to listOf("number", "null")),
            "values" to mapOf("type" to listOf("array", "null"), "items" to string()),
            "valuesEn" to mapOf("type" to listOf("array", "null"), "items" to string()),
            "note" to nullableString(),
            "noteEn" to nullableString(),
        )
        return obj(condition)
    }

    // 일정 블록의 하위 시간표(native-v1.md "트랙·세션 시간표"). 없으면 null
    private fun timetable(): Map<String, Any> = mapOf(
        "type" to listOf("object", "null"),
        "properties" to mapOf(
            "tracks" to mapOf("type" to "array", "items" to obj(mapOf("name" to string(), "room" to nullableString()))),
            "sessions" to mapOf(
                "type" to "array",
                "items" to obj(
                    mapOf(
                        "track" to nullableString(),
                        "kind" to mapOf("type" to listOf("string", "null"), "enum" to listOf("TALK", "KEYNOTE", "BREAK", "MEAL", "OTHER", null)),
                        "title" to string(),
                        "startAt" to nullableString(),
                        "endAt" to nullableString(),
                        "speakers" to mapOf("type" to "array", "items" to obj(mapOf("name" to string(), "affiliation" to nullableString()))),
                        "tags" to mapOf("type" to "array", "items" to string()),
                        "description" to nullableString(),
                    )
                ),
            ),
        ),
        "required" to listOf("tracks", "sessions"),
        "additionalProperties" to false,
    )

    private fun string() = mapOf("type" to "string")
    private fun nullableString() = mapOf("type" to listOf("string", "null"))
    private fun obj(properties: Map<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to properties.keys.toList(),
        "additionalProperties" to false,
    )
}
