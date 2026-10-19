package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.collection.DateLabelExample
import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind
import io.wid.dearby.batch.application.AgentInput
import io.wid.dearby.batch.application.FetchedPage

// 수집 에이전트(Claude Code CLI + 도구 서버)의 지시문과 submit_candidate 입력 스키마
object CollectionPrompts {

    // 제출하는 활동 하나하나에 적용하는 추출 규칙
    private val EXTRACT_RULES = """
        - 활동은 **프로그램의 기수·회차 하나**다. 페이지가 알리는 기수마다 활동 하나를 만든다. 예: "부스트캠프 10기", "Let'Swift 2026".
        - 한 기수 안의 선발 단계·테스트·챌린지·멤버십·과정 단계·세션·교육·오리엔테이션·결과 발표·면접·마감은 활동이 아니라 그 기수 활동의 schedules다.
          예: 부스트캠프 페이지의 문제 해결력 테스트, 베이직, 챌린지, 멤버십은 "부스트캠프 10기" 활동 하나의 schedules다.
        - 트랙은 페이지가 따로 지원하는 별개 기수로 소개할 때만 별도 활동이다(예: 웹·모바일 10기와 AI Tech 8기를 따로 모집하면 둘).
        - roundKey는 그 기수의 식별자다. 같은 기수를 다른 페이지나 다른 날에 다시 만나도 같아야 하므로 기수·연도(·트랙)만 쓴다(예: "10기", "2026", "10기-웹모바일", "8기-AITech"). 페이지 이름이나 단계 이름을 넣지 않는다.
        - schedules[].kind: 서류·신청 마감은 DEADLINE, 결과 발표는 ANNOUNCEMENT, 면접은 INTERVIEW, 테스트·챌린지·교육·행사·세션은 EVENT. 모르면 EVENT.
        - 본문에 적힌 내용만 쓴다. 추측하지 않는다. 모르는 값은 null로 둔다.
        - 시각은 본문에 시각까지 있을 때만 ISO 8601 오프셋 형식(예: 2026-10-08T19:00:00+09:00)으로 쓴다(recruitmentStartAt·recruitmentEndAt, schedules[].startAt·endAt).
        - 두 기간을 구분한다. **모집 기간**(recruitmentStartAt·EndAt 또는 recruitmentStartDate·EndDate)은 지원서·신청을 받는 기간이다(본문의 모집·접수·지원·신청·원서·마감 낱말이 붙은 날짜). **활동 기간**(dateLabel·startDate·endDate)은 교육·과정·행사·운영이 실제로 진행되는 기간이다(교육 기간·과정 기간·행사 일시·운영 기간·개최일). 같은 날짜를 두 기간에 함께 넣지 않는다. 본문에 모집 기간만 있으면 모집 기간 필드만 채우고 dateLabel은 빈 문자열, startDate·endDate는 null로 둔다. 본문에 활동 기간만 있으면 모집 기간 필드는 모두 null이다. 어느 쪽인지 알 수 없는 날짜는 모집 기간에 넣지 않는다.
        - 기간 필드에는 날짜만 적는다. 날짜만 있으면 startDate·endDate(yyyy-MM-dd, 끝 날짜 포함)를 채운다: 활동 기간은 startDate·endDate, 모집 기간은 recruitmentStartDate·recruitmentEndDate, 개별 일정은 schedules[].startDate·endDate. 하루짜리면 둘을 같게 쓴다. 연도가 없으면 본문의 다른 날짜로 알 수 있을 때만 쓰고 모르면 null.
        - dateLabel은 활동 기간의 원문 문구 그대로다(모집 기간 문구를 넣지 않는다). "2026 신입생 모집"처럼 날짜가 아닌 문구는 dateLabel에만 두고 startDate·endDate는 null로 둔다.
        - 다른 프로그램의 활동은 뽑지 않는다. 프로그램 이름과 다른 프로그램(예: 같은 회사의 다른 과정)의 기수가 본문에 있어도 제외한다.
        - 요청에 "활동으로 만들지 않을 이름"이 있으면 그 이름의 활동은 뽑지 않고, "이미 있는 활동의 다른 표기"가 있으면 그 표기 대신 정본 이름을 name으로 쓴다.
        - 요청의 "참고(이전 기수)" 값은 힌트다. 본문에 같은 내용이 있으면 그대로 쓰고, 본문에 없으면 null로 둔다. 본문에 없는 내용을 참고에서 베끼지 않는다.
        - evidenceQuote는 이 활동이 본문에 있다는 근거로, 본문에서 그대로 복사한 10~200자 연속 구절이다.
        - name·description은 한국어로 쓴다. nameEn·descriptionEn은 그 영어 번역이다(고유 명사·행사명은 공식 영문 표기가 본문에 있으면 그대로 쓴다). 번역할 수 없으면 null.
        - participationType: 신청하면 참가하면 REGISTRATION, 심사로 선발하면 SELECTION.
        - tags: 참가자가 이 활동에서 얻는 것을 요청의 "태그 사전" 이름 그대로 최대 5개 고른다. 사전에 없는 말은 쓰지 않고, 알 수 없으면 빈 배열.
        - category: 활동의 성격으로 판단한다. 부트캠프 BOOTCAMP, 컨퍼런스 CONFERENCE, 워크샵 WORKSHOP, 스터디 STUDY, 동아리(연합동아리 등) CLUB, 강연 LECTURE, 해커톤 HACKATHON, 공모전 CONTEST, 밋업 MEETUP, 그 외 OTHER. 모르면 null.
        - audience는 대상자 원문 문장 그대로다. eligibility는 그 대상자를 조건 트리로 정리한 것이다(본문에 대상 조건이 없으면 null).
          - 트리: 그룹은 op("all"=모두 충족, "any"=하나 이상)와 items, 조건은 field(한글 어휘)·fieldEn(영문 어휘)과 범위 from·to(숫자, 포함, 한쪽 생략 가능) 또는 집합 values(한글)·valuesEn(영문, 같은 순서) 중 하나, 선택으로 note·noteEn(원문 구절). 최상위는 그룹이고 3단계까지만 중첩한다.
          - 어휘(한글 → 영문): 나이 → age(만 나이, 숫자 범위), 출생연도 → birthYear(숫자), 학년 → schoolYear(숫자), 학력 → educationLevel(값 고졸 highSchool·대학 university·대학원 graduateSchool), 재학상태 → educationStatus(재학 enrolled·졸업 graduated·휴학 onLeave·졸업예정 graduating), 전공 → major, 지역 → region, 취업상태 → employment(학생 student·미취업 unemployed·재직 employed·창업 founder), 경력연수 → experienceYears(숫자), 국적 → nationality(ISO 3166-1 alpha-2, 예 KR), 팀규모 → teamSize(숫자), 기타 → other(note만).
          - 어휘에 없는 조건은 field "기타", fieldEn "other"로 두고 note에 원문을 적는다. 본문에 없는 조건을 만들지 않는다.
          - 예1 "19세 이상 34세 이하 미취업자 또는 재학생": {"op":"all","items":[{"field":"나이","fieldEn":"age","from":19,"to":34},{"op":"any","items":[{"field":"취업상태","fieldEn":"employment","values":["미취업"],"valuesEn":["unemployed"]},{"field":"재학상태","fieldEn":"educationStatus","values":["재학"],"valuesEn":["enrolled"]}]}]}
          - 예2 "개발 경력 2년 미만, 대한민국 국적, 풀타임 참여 가능": {"op":"all","items":[{"field":"경력연수","fieldEn":"experienceYears","to":2},{"field":"국적","fieldEn":"nationality","values":["대한민국"],"valuesEn":["KR"]},{"field":"기타","fieldEn":"other","note":"풀타임 참여 가능"}]}
    """.trimIndent()

    // 도구 결과로 보이는 본문 길이. Claude Code는 MCP 도구 결과를 기본 25,000토큰에서 자르고, 받은 페이지는 루프가 끝날 때까지 대화에 남는다.
    // 원문 대조(ActivityVerifier)는 자르지 않은 본문으로 한다
    const val MAX_PAGE_CHARS = 40_000

    // 프로그램별 힌트(줄마다 앞에 줄바꿈). 조심 목록(admin-v1.md)은 힌트로만 넘긴다. 저장 전 이름 대조가 보장이다
    private fun hints(watchlist: List<WatchEntry>, previous: PreviousEdition?, examples: List<DateLabelExample>, tagNames: List<String>) = buildString {
        // 태그 사전(native-v1.md "태그"): 이 이름 중에서만 고른다. 사전 밖 이름은 저장할 때 버린다
        tagNames.takeIf { it.isNotEmpty() }?.let { append("\n태그 사전(tags는 이 중에서만 고른다): ${it.joinToString(", ")}") }
        // 날짜 문구 피드백(admin-v1.md): 운영자가 바로잡은 문구 → 날짜 쌍을 해석 예시로 보인다. 지정된 일정 자체는 저장할 때 따로 지킨다
        examples.takeIf { it.isNotEmpty() }?.let { list ->
            append("\n날짜 문구 해석 예시(운영자가 바로잡은 것):")
            list.forEach { append("\n- ").append(dateExampleLine(it)) }
            append("\n비슷한 문구는 같은 방식으로 날짜 필드를 채운다. 연도·월이 생략된 문구는 본문의 다른 날짜로 알 수 있을 때만 채우고 모르면 null.")
        }
        // 이전 기수(admin-v1.md "이전 기수 정보 자동완성"): 본문에 같은 내용이 있을 때 같은 어휘로 쓰게 하는 힌트. 서버가 비어 있는 필드를 따로 채운다
        previous?.takeIf { it.eligibility != null || it.audience != null }?.let { edition ->
            append("\n참고(이전 기수 \"${edition.name}\"):")
            edition.audience?.let { append("\n- 대상 원문: $it") }
            edition.eligibility?.let { append("\n- 대상자 조건: ${EligibilityJson.write(it)}") }
        }
        watchlist.filter { it.kind == WatchKind.EXCLUDE }.takeIf { it.isNotEmpty() }?.let { excluded ->
            append("\n활동으로 만들지 않을 이름: ${excluded.joinToString(", ") { it.name }}")
        }
        watchlist.filter { it.kind == WatchKind.ALIAS && it.canonicalActivityName != null }.takeIf { it.isNotEmpty() }?.let { aliases ->
            append("\n이미 있는 활동의 다른 표기: ${aliases.joinToString(", ") { "${it.name} → ${it.canonicalActivityName}" }}")
        }
    }

    // 에이전트 루프: 페이지를 직접 골라 받고, 활동을 하나씩 제출해 서버 판정을 받는다
    val AGENT_SYSTEM = """
        한국에서 열리는 활동(행사·교육·공모·모집)을 주어진 프로그램의 공식 공지에서 수집한다. 도구로 페이지를 받아 읽고, 찾은 활동을 하나씩 제출한다.
        진행:
        - 시작 주소가 있으면 fetch_page로 먼저 받는다. 없거나 부족하면 WebSearch로 현재 또는 다가오는 회차의 모집 공지·신청 안내·일정 안내 페이지를 찾는다. 허용 도메인이 주어지면 그 안의 주소만 받을 수 있다. 없으면 주최 기관의 공식 사이트를 블로그·뉴스·모음 사이트보다 우선한다.
        - 받은 페이지의 링크 목록에서 모집 공지·상세 안내로 보이는 주소를 골라 더 받는다. 관계없는 페이지는 받지 않는다. 받을 수 있는 페이지 수는 정해져 있다.
        - fetch_page 결과 본문이 비었거나 아주 짧으면(자바스크립트로 그리는 페이지) 같은 주소를 render_page로 받는다. 그 밖에는 fetch_page를 쓴다.
        - 활동을 찾으면 submit_candidate로 하나씩 제출한다. sourceUrl은 그 활동의 근거 구절이 있는 페이지로, 이번에 fetch_page·render_page로 받은 주소 그대로 쓴다. 제출하면 서버가 원문과 대조해 저장 여부와 거절 이유를 돌려준다. 거절 이유를 고칠 수 있으면(예: 근거 구절을 본문에서 그대로 다시 복사) 고쳐서 다시 제출하고, 고칠 수 없으면 넘어간다.
        - 더 받을 페이지나 제출할 활동이 없으면 finish로 끝낸다.
        추출 규칙(제출하는 활동 하나하나에 적용):
    """.trimIndent() + "\n" + EXTRACT_RULES

    fun agentRequest(input: AgentInput) = buildString {
        val target = input.target
        append("프로그램: ${target.programName}")
        target.organizationName?.let { append("\n주최 기관: $it") }
        if (target.officialUrls.isNotEmpty()) append("\n시작 주소: ${target.officialUrls.joinToString(", ")}")
        if (target.officialOnly) append("\n허용 도메인: ${target.allowedHosts.joinToString(", ")}")
        append(hints(input.watchlist, input.previous, input.examples, input.tagNames))
    }

    // 에이전트가 받은 페이지를 모델에게 보이는 형태: 자른 본문 + 따라갈 링크
    fun pageResult(url: String, page: FetchedPage) = buildString {
        append("주소: $url")
        append("\n\n<page>\n${page.text.take(MAX_PAGE_CHARS)}\n</page>")
        if (page.links.isNotEmpty()) append("\n\n링크:\n${page.links.take(MAX_LINKS).joinToString("\n")}")
    }

    private const val MAX_LINKS = 200

    // `[활동] "2026년 11월 1일~15일" → startDate 2026-11-01, endDate 2026-11-15`; 시각이 있으면 startAt/endAt(ISO 8601 오프셋)
    fun dateExampleLine(example: DateLabelExample): String {
        val slot = when (example.slot) { "APPLICATION" -> "모집"; "ACTIVITY" -> "활동"; else -> "일정" }
        val value = if (example.startAt != null) "startAt ${example.startAt}, endAt ${example.endAt ?: example.startAt}"
            else "startDate ${example.startDate}, endDate ${example.endDate ?: example.startDate}"
        val reason = example.note?.let { " (이유: $it)" } ?: ""
        return "[$slot] \"${example.dateLabel}\" → $value$reason"
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
                    )
                ),
            ),
            "location" to nullableString(),
            "cost" to nullableString(),
            "audience" to nullableString(),
            "tags" to mapOf("type" to "array", "items" to string()),
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

    private fun string() = mapOf("type" to "string")
    private fun nullableString() = mapOf("type" to listOf("string", "null"))
    private fun obj(properties: Map<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to properties.keys.toList(),
        "additionalProperties" to false,
    )
}
