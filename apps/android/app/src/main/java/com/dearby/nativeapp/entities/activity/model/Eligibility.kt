package com.dearby.nativeapp.entities.activity.model

/**
 * 대상자 조건(contract "대상자 조건(eligibility)"): a nested group of conditions. JSON keys are camelCase (`field`, `fieldEn`,
 * `from`, `to`, `values`, `valuesEn`, `note`, `noteEn`); [EligibilityCondition.field] is the Korean vocabulary word and [EligibilityCondition.fieldEn] its English twin.
 */
sealed interface EligibilityItem

/** [op] is `all` (every item) or `any` (at least one). */
data class EligibilityGroup(val op: String, val items: List<EligibilityItem>) : EligibilityItem

/** A range ([from]·[to], either side open) or a set of Korean [values]; `기타` carries only [note]. */
data class EligibilityCondition(
    val field: String, val fieldEn: String, val from: Double? = null, val to: Double? = null,
    val values: List<String> = emptyList(), val note: String? = null,
) : EligibilityItem

/** Korean lines for the activity detail's 대상 section: one line per top-level item, `any` groups joined with 또는. */
fun EligibilityGroup.lines(): List<String> =
    if (op == "any") listOfNotNull(phrase()) else items.mapNotNull { it.phrase() }

private fun EligibilityItem.phrase(): String? = when (this) {
    is EligibilityCondition -> phrase()
    is EligibilityGroup -> items.mapNotNull { it.phrase() }.takeIf { it.isNotEmpty() }?.joinToString(if (op == "any") " 또는 " else ", ")
}

private fun EligibilityCondition.phrase(): String? {
    val n = { value: Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString() }
    fun range(unit: String, label: String? = null, over: String = "이상", under: String = "이하"): String? {
        val prefix = label?.let { "$it " } ?: ""
        return when {
            from != null && to != null -> "$prefix${n(from)}~${n(to)}$unit"
            from != null -> "$prefix${n(from)}$unit $over"
            to != null -> "$prefix${n(to)}$unit $under"
            else -> null
        }
    }
    val text = when (fieldEn) {
        "age" -> range("세", "나이")
        "experience_years" -> range("년", "경력")
        "school_year" -> range("학년")
        "birth_year" -> range("년", over = "이후 출생", under = "이전 출생")?.let { if (from != null && to != null) "$it 출생" else it }
        "team_size" -> range("명", "팀")
        "other" -> null
        else -> range("", field)
    }
    return text
        ?: values.takeIf { it.isNotEmpty() }?.let { "$field ${it.joinToString("·")}" }
        ?: note?.takeIf { it.isNotBlank() }
}
