package io.wid.dearby.domain.eligibility

import io.wid.dearby.domain.InvalidInputException
import java.math.BigDecimal

// 대상자 검색 조건(native-v1.md "대상자 조건 > 검색"). 쿼리 키는 영문 어휘(camelCase)이고 필드마다 값 하나다
data class EligibilityFilter(
    val numeric: Map<EligibilityField, BigDecimal>,
    val values: Map<EligibilityField, String>,
) {
    val fields: Set<EligibilityField> get() = numeric.keys + values.keys

    companion object {
        private const val MAX_VALUE_LENGTH = 100

        // 요청 파라미터에서 대상자 필드만 골라 만든다. 하나도 없으면 null. 숫자가 아니거나 비어 있으면 400
        fun from(params: Map<String, String?>): EligibilityFilter? {
            val numeric = mutableMapOf<EligibilityField, BigDecimal>()
            val values = mutableMapOf<EligibilityField, String>()
            for (field in EligibilityField.searchable) {
                val raw = params[field.en]?.trim() ?: continue
                if (raw.isEmpty() || raw.length > MAX_VALUE_LENGTH) throw InvalidInputException("${field.en} 값이 올바르지 않습니다")
                when (field.kind) {
                    EligibilityField.Kind.NUMERIC -> numeric[field] = raw.toBigDecimalOrNull() ?: throw InvalidInputException("${field.en}은(는) 숫자여야 합니다")
                    EligibilityField.Kind.SET -> values[field] = raw
                    EligibilityField.Kind.NOTE -> Unit
                }
            }
            return if (numeric.isEmpty() && values.isEmpty()) null else EligibilityFilter(numeric, values)
        }
    }
}
