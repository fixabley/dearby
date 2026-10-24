package io.wid.dearby.application.discovery

import io.wid.dearby.domain.eligibility.EligibilityCondition
import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.eligibility.EligibilityGroup
import io.wid.dearby.domain.eligibility.EligibilityNode
import io.wid.dearby.domain.eligibility.EligibilityOp
import java.math.BigDecimal

// 대기열 ELIGIBILITY 장면의 아이콘 라벨(native-v1.md "맞춤 대기열", 2026-10-25 사용자 "참여대상이 문자열로 나오는데 아이콘 라벨로").
// 조건 하나가 항목 하나, any 그룹은 " 또는 "으로 한 항목. 중요도 순(나이 > 학력·재학 > 취업 > 학년 > 지역 > 그 밖)으로 최대 4개
data class EligibilityItem(val icon: String, val text: String)

object EligibilityLabels {
    private const val MAX_ITEMS = 4
    private const val MAX_NOTE = 20

    private class Labeled(val rank: Int, val icon: String, val text: String)

    fun of(group: EligibilityGroup?): List<EligibilityItem> =
        group?.let(::flatten).orEmpty().sortedBy { it.rank }.take(MAX_ITEMS).map { EligibilityItem(it.icon, it.text) }

    // all 그룹은 펼치고, any 그룹은 한 항목으로 묶는다
    private fun flatten(node: EligibilityNode): List<Labeled> = when (node) {
        is EligibilityCondition -> listOfNotNull(label(node))
        is EligibilityGroup -> if (node.op == EligibilityOp.ALL) node.items.flatMap(::flatten) else {
            val parts = node.items.flatMap(::flatten)
            parts.minByOrNull { it.rank }?.let { first -> listOf(Labeled(first.rank, first.icon, parts.joinToString(" 또는 ") { it.text })) }.orEmpty()
        }
    }

    private fun label(c: EligibilityCondition): Labeled? {
        fun n(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
        fun range(unit: String, prefix: String = "", suffix: String = ""): String? = when {
            c.from != null && c.to != null -> "$prefix${n(c.from!!)}~${n(c.to!!)}$unit$suffix"
            c.to != null -> "$prefix${n(c.to!!)}$unit 이하$suffix"
            c.from != null -> "$prefix${n(c.from!!)}$unit 이상$suffix"
            else -> null
        }
        val values = c.values.map { it.trim() }.filter { it.isNotEmpty() }
        return when (c.field) {
            EligibilityField.AGE -> range("세", prefix = "만 ")?.let { Labeled(0, "age", it) }
            EligibilityField.BIRTH_YEAR -> when {
                c.from != null && c.to != null -> "${n(c.from!!)}~${n(c.to!!)}년생"
                c.from != null -> "${n(c.from!!)}년 이후 출생"
                c.to != null -> "${n(c.to!!)}년 이전 출생"
                else -> null
            }?.let { Labeled(0, "age", it) }
            EligibilityField.EDUCATION_STATUS -> people(values, mapOf("재학" to "재학생", "휴학" to "휴학생", "졸업" to "졸업생", "졸업예정" to "졸업예정자"))?.let { Labeled(1, "education", it) }
            EligibilityField.EDUCATION_LEVEL -> values.takeIf { it.isNotEmpty() }?.let { Labeled(1, "education", "${it.joinToString("·")} 학력") }
            EligibilityField.EMPLOYMENT -> people(values, mapOf("학생" to "학생", "미취업" to "미취업자", "재직" to "재직자", "창업" to "창업자"))?.let { Labeled(2, "employment", it) }
            EligibilityField.SCHOOL_YEAR -> range("학년")?.let { Labeled(3, "schoolYear", it) }
            EligibilityField.REGION -> values.takeIf { it.isNotEmpty() }?.let { Labeled(4, "region", it.joinToString("·")) }
            EligibilityField.MAJOR -> values.takeIf { it.isNotEmpty() }?.let {
                Labeled(5, "major", if (it.any { v -> "무관" in v }) "전공 무관" else "${it.joinToString("·")} 전공")
            }
            EligibilityField.NATIONALITY -> values.takeIf { it.isNotEmpty() }?.let { Labeled(5, "nationality", "${it.joinToString("·")} 국적") }
            EligibilityField.EXPERIENCE_YEARS -> range("년", prefix = "경력 ")?.let { Labeled(5, "experience", it) }
            EligibilityField.TEAM_SIZE -> range("인", suffix = " 팀")?.let { Labeled(5, "team", it) }
            EligibilityField.OTHER -> c.note?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NOTE }?.let { Labeled(6, "other", it) }
        }
    }

    // "재학생·휴학생" → "재학·휴학생"처럼 같은 끝 글자를 한 번만
    private fun people(values: List<String>, names: Map<String, String>): String? {
        val labels = values.map { names[it] ?: it }.ifEmpty { return null }
        val suffix = labels.last().last()
        return if (labels.size > 1 && labels.all { it.length > 1 && it.last() == suffix }) labels.dropLast(1).joinToString("") { it.dropLast(1) + "·" } + labels.last()
        else labels.joinToString("·")
    }
}
