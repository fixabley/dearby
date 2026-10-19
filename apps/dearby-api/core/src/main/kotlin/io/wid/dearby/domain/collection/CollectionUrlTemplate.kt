package io.wid.dearby.domain.collection

import java.time.LocalDate

// 수집 시작 주소의 토큰(admin-v1.md "주소 토큰", 2026-10-19). 회차마다 주소가 바뀌는 프로그램(toss.im/slash-23, slash-24)을
// `{yy}`·`{year}`·`{round}`로 적고 수집 때 실제 주소로 펼친다. 순수 함수만 두고 시각·DB는 호출자가 넘긴다
object CollectionUrlTemplate {
    const val YEAR = "year" // 네 자리 연도
    const val YY = "yy" // 두 자리 연도
    const val ROUND = "round" // 기수 번호(이전 활동 이름의 "N기"에서 읽는다)

    private val TOKEN = Regex("\\{([^{}]*)}")
    private val KNOWN = setOf(YEAR, YY, ROUND)
    private val ROUND_IN_NAME = Regex("(\\d+)\\s*기")
    private val EDGE_NUMBER = Regex("^(\\d+)\\b|\\b(\\d+)$")

    fun tokens(url: String): List<String> = TOKEN.findAll(url).map { it.groupValues[1] }.distinct().toList()

    fun unknownTokens(url: String): List<String> = tokens(url).filterNot { it in KNOWN }

    fun needsRound(url: String): Boolean = ROUND in tokens(url)

    // 검증용: 토큰을 샘플 값으로 바꾼 주소(https·호스트 검사는 이 값으로 한다)
    fun sample(url: String): String = fill(url, 2026, 1)

    // 올해·내년(연도 토큰), 다음 기수·현재 기수(기수 토큰) 순으로 펼친다. 둘 다 있으면 조합(최대 4개).
    // 토큰이 없으면 그 주소 하나. {round}가 있는데 기수를 모르면 빈 목록(호출자가 거절 이유로 남긴다)
    fun expand(url: String, today: LocalDate, previousRound: Int?): List<String> {
        val tokens = tokens(url)
        if (tokens.isEmpty()) return listOf(url)
        if (ROUND in tokens && previousRound == null) return emptyList()
        val years = if (YEAR in tokens || YY in tokens) listOf(today.year + 1, today.year) else listOf(today.year)
        val rounds = if (ROUND in tokens) listOf(previousRound!! + 1, previousRound) else listOf(0)
        return years.flatMap { year -> rounds.map { round -> fill(url, year, round) } }.distinct()
    }

    // 활동 이름에서 기수 번호: "10기"가 있으면 그 수, 없으면 이름 맨 앞이나 맨 뒤의 숫자("SLASH 24" → 24), 둘 다 없으면 null
    fun previousRound(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        ROUND_IN_NAME.find(name)?.let { return it.groupValues[1].toIntOrNull() }
        val edge = EDGE_NUMBER.find(name.trim()) ?: return null
        return (edge.groupValues[1].ifEmpty { edge.groupValues[2] }).toIntOrNull()
    }

    private fun fill(url: String, year: Int, round: Int): String =
        url.replace("{$YEAR}", year.toString()).replace("{$YY}", "%02d".format(year % 100)).replace("{$ROUND}", round.toString())
}
