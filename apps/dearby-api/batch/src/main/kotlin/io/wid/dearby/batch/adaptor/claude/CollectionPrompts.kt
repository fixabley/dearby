package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.domain.collection.CollectionTarget
import java.net.URI

// API·CLI 두 방식이 같은 지시문과 출력 스키마를 쓴다
object CollectionPrompts {

    val DISCOVER_SYSTEM = """
        한국에서 열리는 활동(행사·교육·공모·모집)의 공지를 찾는다.
        주어진 프로그램의 현재 또는 다가오는 회차 모집 공지, 신청 안내, 일정 안내 페이지를 웹 검색으로 찾아라.
        허용 도메인이 주어지면 그 안에서만 검색한다. 없으면 주최 기관의 공식 사이트를 가장 먼저 찾고,
        블로그·뉴스·모음 사이트보다 공식 공지를 우선한다. 찾은 페이지를 짧게 나열하고 끝낸다.
    """.trimIndent()

    fun discoverRequest(target: CollectionTarget) = buildString {
        append("프로그램: ${target.programName}")
        target.organizationName?.let { append("\n주최 기관: $it") }
        target.officialUrl?.let { append("\n공식 안내 페이지: $it") }
        if (target.officialOnly) append("\n허용 도메인: ${target.allowedHosts.joinToString(", ")}")
    }

    // 찾은 주소 중 https이고, 허용 호스트가 있으면 그 안의 것만 쓴다
    fun usableUrls(urls: List<String>, target: CollectionTarget): List<String> =
        urls.filter { url ->
            val uri = runCatching { URI(url) }.getOrNull() ?: return@filter false
            uri.scheme == "https" && (!target.officialOnly || uri.host?.lowercase() in target.allowedHosts.map { it.lowercase() })
        }.distinct()

    // CLI는 검색 결과 블록을 직접 주지 않아 찾은 주소를 이 형식으로 받는다
    val DISCOVER_SCHEMA: Map<String, Any> = obj(mapOf("urls" to mapOf("type" to "array", "items" to mapOf("type" to "string"))))

    val EXTRACT_SYSTEM = """
        페이지 본문에서 이 프로그램의 활동을 뽑는다.
        - 활동은 **프로그램의 기수·회차 하나**다. 페이지가 알리는 기수마다 활동 하나를 만든다. 예: "부스트캠프 10기", "Let'Swift 2026".
        - 한 기수 안의 선발 단계·테스트·챌린지·멤버십·과정 단계·세션·교육·오리엔테이션·결과 발표·면접·마감은 활동이 아니라 그 기수 활동의 schedules다.
          예: 부스트캠프 페이지의 문제 해결력 테스트, 베이직, 챌린지, 멤버십은 "부스트캠프 10기" 활동 하나의 schedules다.
        - 트랙은 페이지가 따로 지원하는 별개 기수로 소개할 때만 별도 활동이다(예: 웹·모바일 10기와 AI Tech 8기를 따로 모집하면 둘).
        - roundKey는 그 기수의 식별자다. 같은 기수를 다른 페이지나 다른 날에 다시 만나도 같아야 하므로 기수·연도(·트랙)만 쓴다(예: "10기", "2026", "10기-웹모바일", "8기-AITech"). 페이지 이름이나 단계 이름을 넣지 않는다.
        - schedules[].kind: 서류·신청 마감은 DEADLINE, 결과 발표는 ANNOUNCEMENT, 면접은 INTERVIEW, 테스트·챌린지·교육·행사·세션은 EVENT. 모르면 EVENT.
        - 본문에 적힌 내용만 쓴다. 추측하지 않는다. 모르는 값은 null로 둔다.
        - 시각은 본문에 시각까지 있을 때만 ISO 8601 오프셋 형식(예: 2026-10-08T19:00:00+09:00)으로 쓴다. 날짜만 있으면 시각은 null로 두고 dateLabel에 원문 문구를 쓴다.
        - evidenceQuote는 이 활동이 본문에 있다는 근거로, 본문에서 그대로 복사한 10~200자 연속 구절이다.
        - name·description은 한국어로 쓴다. nameEn·descriptionEn은 그 영어 번역이다(고유 명사·행사명은 공식 영문 표기가 본문에 있으면 그대로 쓴다). 번역할 수 없으면 null.
        - participationType: 신청하면 참가하면 REGISTRATION, 심사로 선발하면 SELECTION.
        - 활동이 없으면 activities를 빈 배열로 둔다.
    """.trimIndent()

    const val MAX_PAGE_CHARS = 150_000

    fun extractRequest(programName: String, url: String, text: String) =
        "프로그램: $programName\n페이지: $url\n\n<page>\n${text.take(MAX_PAGE_CHARS)}\n</page>"

    val EXTRACT_SCHEMA: Map<String, Any> = obj(
        mapOf(
            "activities" to mapOf(
                "type" to "array",
                "items" to obj(
                    mapOf(
                        "roundKey" to string(),
                        "name" to string(),
                        "nameEn" to nullableString(),
                        "description" to string(),
                        "descriptionEn" to nullableString(),
                        "participationType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("REGISTRATION", "SELECTION", null)),
                        "applicationUrl" to nullableString(),
                        "recruitmentStartAt" to nullableString(),
                        "recruitmentEndAt" to nullableString(),
                        "dateLabel" to string(),
                        "schedules" to mapOf(
                            "type" to "array",
                            "items" to obj(
                                mapOf(
                                    "title" to string(),
                                    "kind" to mapOf("type" to listOf("string", "null"), "enum" to listOf("DEADLINE", "ANNOUNCEMENT", "INTERVIEW", "EVENT", null)),
                                    "startAt" to nullableString(),
                                    "endAt" to nullableString(),
                                    "dateLabel" to string(),
                                )
                            ),
                        ),
                        "location" to nullableString(),
                        "cost" to nullableString(),
                        "audience" to nullableString(),
                        "evidenceQuote" to string(),
                    )
                ),
            ),
        )
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
