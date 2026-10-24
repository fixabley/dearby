package io.wid.dearby.domain.recommendation

import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.eligibility.EligibilityFilter
import java.time.OffsetDateTime

// 지원 자격 정보(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"). 값은 대상자 조건 어휘의 영문 값이다. 별도 동의 뒤에만 저장하고 로그·응답 어디에도 남기지 않는다(본인 GET만)
data class EligibilityProfile(
    val educationStatus: String?, // enrolled·onLeave·graduating·graduated
    val employment: String?, // student·unemployed·employed·founder
    val schoolYear: Int?, // 1~5(5는 5학년 이상)
    val birthYear: Int?,
    val consentVersion: String,
    val consentedAt: OffsetDateTime,
) {
    // 대상자 조건 1·2차 판정에 넣을 검색 조건. 나이는 올해(서울) − 태어난 해. 값이 하나도 없으면 null(거르지 않는다)
    fun filter(currentYear: Int): EligibilityFilter? {
        val numeric = buildMap {
            schoolYear?.let { put(EligibilityField.SCHOOL_YEAR, it.toBigDecimal()) }
            birthYear?.let {
                put(EligibilityField.BIRTH_YEAR, it.toBigDecimal())
                put(EligibilityField.AGE, (currentYear - it).toBigDecimal())
            }
        }
        val values = buildMap {
            // 퍼널의 "직장에 다녀요"는 재학상태를 보내지 않는다. 재학 조건에 맞지 않게 졸업으로 본다(2026-10-24 결정)
            (educationStatus ?: GRADUATED.takeIf { employment == EMPLOYED })?.let { put(EligibilityField.EDUCATION_STATUS, it) }
            employment?.let { put(EligibilityField.EMPLOYMENT, it) }
        }
        return if (numeric.isEmpty() && values.isEmpty()) null else EligibilityFilter(numeric, values)
    }

    companion object {
        // 지원 자격 정보 수집·이용 동의 문서 버전(시행일). 문서를 바꾸면 올리고, 앱은 다음 입력 때 다시 동의 시트를 띄운다
        const val CONSENT_VERSION = "2026-10-24"
        const val MAX_SCHOOL_YEAR = 5
        const val MIN_BIRTH_YEAR = 1900
        private const val EMPLOYED = "employed"
        private const val GRADUATED = "graduated"
    }
}
