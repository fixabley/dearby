package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.domain.collection.CollectionTarget
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind
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
        - 시각은 본문에 시각까지 있을 때만 ISO 8601 오프셋 형식(예: 2026-10-08T19:00:00+09:00)으로 쓴다(recruitmentStartAt·recruitmentEndAt, schedules[].startAt·endAt).
        - 기간 필드에는 날짜만 적는다. 날짜만 있으면 startDate·endDate(yyyy-MM-dd, 끝 날짜 포함)를 채운다: 활동 기간은 startDate·endDate, 모집 기간은 recruitmentStartDate·recruitmentEndDate, 개별 일정은 schedules[].startDate·endDate. 하루짜리면 둘을 같게 쓴다. 연도가 없으면 본문의 다른 날짜로 알 수 있을 때만 쓰고 모르면 null.
        - dateLabel은 원문 문구 그대로다. "2026 신입생 모집"처럼 날짜가 아닌 문구는 dateLabel에만 두고 startDate·endDate는 null로 둔다.
        - 다른 프로그램의 활동은 뽑지 않는다. 프로그램 이름과 다른 프로그램(예: 같은 회사의 다른 과정)의 기수가 본문에 있어도 제외한다.
        - 요청에 "활동으로 만들지 않을 이름"이 있으면 그 이름의 활동은 뽑지 않고, "이미 있는 활동의 다른 표기"가 있으면 그 표기 대신 정본 이름을 name으로 쓴다.
        - evidenceQuote는 이 활동이 본문에 있다는 근거로, 본문에서 그대로 복사한 10~200자 연속 구절이다.
        - name·description은 한국어로 쓴다. nameEn·descriptionEn은 그 영어 번역이다(고유 명사·행사명은 공식 영문 표기가 본문에 있으면 그대로 쓴다). 번역할 수 없으면 null.
        - participationType: 신청하면 참가하면 REGISTRATION, 심사로 선발하면 SELECTION.
        - audience는 대상자 원문 문장 그대로다. eligibility는 그 대상자를 조건 트리로 정리한 것이다(본문에 대상 조건이 없으면 null).
          - 트리: 그룹은 op("all"=모두 충족, "any"=하나 이상)와 items, 조건은 field(한글 어휘)·fieldEn(영문 어휘)과 범위 from·to(숫자, 포함, 한쪽 생략 가능) 또는 집합 values(한글)·valuesEn(영문, 같은 순서) 중 하나, 선택으로 note·noteEn(원문 구절). 최상위는 그룹이고 3단계까지만 중첩한다.
          - 어휘(한글 → 영문): 나이 → age(만 나이, 숫자 범위), 출생연도 → birthYear(숫자), 학년 → schoolYear(숫자), 학력 → educationLevel(값 고졸 highSchool·대학 university·대학원 graduateSchool), 재학상태 → educationStatus(재학 enrolled·졸업 graduated·휴학 onLeave·졸업예정 graduating), 전공 → major, 지역 → region, 취업상태 → employment(학생 student·미취업 unemployed·재직 employed·창업 founder), 경력연수 → experienceYears(숫자), 국적 → nationality(ISO 3166-1 alpha-2, 예 KR), 팀규모 → teamSize(숫자), 기타 → other(note만).
          - 어휘에 없는 조건은 field "기타", fieldEn "other"로 두고 note에 원문을 적는다. 본문에 없는 조건을 만들지 않는다.
          - 예1 "19세 이상 34세 이하 미취업자 또는 재학생": {"op":"all","items":[{"field":"나이","fieldEn":"age","from":19,"to":34},{"op":"any","items":[{"field":"취업상태","fieldEn":"employment","values":["미취업"],"valuesEn":["unemployed"]},{"field":"재학상태","fieldEn":"educationStatus","values":["재학"],"valuesEn":["enrolled"]}]}]}
          - 예2 "개발 경력 2년 미만, 대한민국 국적, 풀타임 참여 가능": {"op":"all","items":[{"field":"경력연수","fieldEn":"experienceYears","to":2},{"field":"국적","fieldEn":"nationality","values":["대한민국"],"valuesEn":["KR"]},{"field":"기타","fieldEn":"other","note":"풀타임 참여 가능"}]}
        - 활동이 없으면 activities를 빈 배열로 둔다.
    """.trimIndent()

    const val MAX_PAGE_CHARS = 150_000

    // 조심 목록(admin-v1.md)은 힌트로만 넘긴다. 저장 전 이름 대조가 보장이다
    fun extractRequest(programName: String, url: String, text: String, watchlist: List<WatchEntry> = emptyList()) = buildString {
        append("프로그램: $programName\n페이지: $url")
        watchlist.filter { it.kind == WatchKind.EXCLUDE }.takeIf { it.isNotEmpty() }?.let { excluded ->
            append("\n활동으로 만들지 않을 이름: ${excluded.joinToString(", ") { it.name }}")
        }
        watchlist.filter { it.kind == WatchKind.ALIAS && it.canonicalActivityName != null }.takeIf { it.isNotEmpty() }?.let { aliases ->
            append("\n이미 있는 활동의 다른 표기: ${aliases.joinToString(", ") { "${it.name} → ${it.canonicalActivityName}" }}")
        }
        append("\n\n<page>\n${text.take(MAX_PAGE_CHARS)}\n</page>")
    }

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
                                )
                            ),
                        ),
                        "location" to nullableString(),
                        "cost" to nullableString(),
                        "audience" to nullableString(),
                        "eligibility" to eligibilityGroup(depth = 1),
                        "evidenceQuote" to string(),
                    )
                ),
            ),
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

    private fun string() = mapOf("type" to "string")
    private fun nullableString() = mapOf("type" to listOf("string", "null"))
    private fun obj(properties: Map<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to properties.keys.toList(),
        "additionalProperties" to false,
    )
}
