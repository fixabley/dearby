package io.wid.dearby.domain.eligibility

import java.math.BigDecimal

// 대상자 조건 트리(native-v1.md "대상자 조건"). 최상위는 그룹, 깊이는 3까지
sealed interface EligibilityNode

enum class EligibilityOp { ALL, ANY }

data class EligibilityGroup(val op: EligibilityOp, val items: List<EligibilityNode>) : EligibilityNode {

    // 2차 판정: 요청한 필드의 조건만 보고 op대로 평가한다. 요청하지 않은 필드의 조건은 참이다
    fun accepts(filter: EligibilityFilter): Boolean = when (op) {
        EligibilityOp.ALL -> items.all { it.accepts(filter) }
        EligibilityOp.ANY -> items.isEmpty() || items.any { it.accepts(filter) }
    }

    // 검색용으로 펼친 조건(그룹 구조는 버린다). 기타는 검색하지 않는다
    fun terms(): List<EligibilityTerm> = items.flatMap {
        when (it) {
            is EligibilityGroup -> it.terms()
            is EligibilityCondition -> it.terms()
        }
    }

    companion object {
        const val MAX_DEPTH = 3
    }
}

data class EligibilityCondition(
    val field: EligibilityField,
    val from: BigDecimal? = null, // 숫자 범위 시작(포함)
    val to: BigDecimal? = null, // 숫자 범위 끝(포함)
    val values: List<String> = emptyList(), // 집합 값(한글)
    val valuesEn: List<String> = emptyList(), // 집합 값(영문), values와 같은 길이
    val note: String? = null,
    val noteEn: String? = null,
) : EligibilityNode {

    fun accepts(filter: EligibilityFilter): Boolean = when (field.kind) {
        EligibilityField.Kind.NUMERIC -> filter.numeric[field]?.let { value -> (from == null || from <= value) && (to == null || value <= to) } ?: true
        EligibilityField.Kind.SET -> filter.values[field]?.let { value -> valuesEn.any { it.equals(value, ignoreCase = true) } } ?: true
        EligibilityField.Kind.NOTE -> true
    }

    fun terms(): List<EligibilityTerm> = when (field.kind) {
        EligibilityField.Kind.NUMERIC -> listOf(EligibilityTerm(field, from, to, null))
        EligibilityField.Kind.SET -> valuesEn.map { EligibilityTerm(field, null, null, it) }
        EligibilityField.Kind.NOTE -> emptyList()
    }
}

private fun EligibilityNode.accepts(filter: EligibilityFilter): Boolean = when (this) {
    is EligibilityGroup -> accepts(filter)
    is EligibilityCondition -> accepts(filter)
}

// activity_eligibility_terms 한 행
data class EligibilityTerm(val field: EligibilityField, val from: BigDecimal?, val to: BigDecimal?, val valueEn: String?)
