package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ELIGIBILITY_TERMS
import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.eligibility.EligibilityFilter
import io.wid.dearby.domain.eligibility.EligibilityGroup
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

// 대상자 조건의 저장(activities.eligibility + activity_eligibility_terms)과 1차 검색 조건. 초안 저장과 관리자 수정이 함께 쓴다
object EligibilityRows {

    fun jsonb(group: EligibilityGroup?): JSONB? = group?.let { JSONB.valueOf(EligibilityJson.write(it)) }

    fun read(value: JSONB?): EligibilityGroup? = value?.data()?.let(EligibilityJson::read)

    // 조건을 다시 펼쳐 쓴다(그룹 구조는 무시). 조건이 없으면 비운다
    fun writeTerms(dsl: DSLContext, activityId: Id, group: EligibilityGroup?) {
        dsl.deleteFrom(ACTIVITY_ELIGIBILITY_TERMS).where(ACTIVITY_ELIGIBILITY_TERMS.ACTIVITY_ID.eq(activityId)).execute()
        group?.terms()?.forEach { term ->
            dsl.insertInto(ACTIVITY_ELIGIBILITY_TERMS)
                .set(ACTIVITY_ELIGIBILITY_TERMS.ACTIVITY_ID, activityId)
                .set(ACTIVITY_ELIGIBILITY_TERMS.FIELD_EN, term.field.en) // 열은 snake_case, 값은 camelCase 어휘
                .set(ACTIVITY_ELIGIBILITY_TERMS.FROM_VALUE, term.from)
                .set(ACTIVITY_ELIGIBILITY_TERMS.TO_VALUE, term.to)
                .set(ACTIVITY_ELIGIBILITY_TERMS.VALUE_EN, term.valueEn)
                .execute()
        }
    }

    // 1차 판정(native-v1.md "대상자 조건 > 검색"): 요청한 필드마다, 그 필드의 조건이 없으면 통과하고 있으면 하나라도 맞아야 한다
    fun conditions(filter: EligibilityFilter?): List<Condition> {
        if (filter == null) return emptyList()
        val t = ACTIVITY_ELIGIBILITY_TERMS
        fun forField(field: EligibilityField, matches: Condition): Condition {
            val anyTerm = DSL.exists(DSL.selectOne().from(t).where(t.ACTIVITY_ID.eq(ACTIVITIES.ID), t.FIELD_EN.eq(field.en)))
            val matching = DSL.exists(DSL.selectOne().from(t).where(t.ACTIVITY_ID.eq(ACTIVITIES.ID), t.FIELD_EN.eq(field.en), matches))
            return DSL.not(anyTerm).or(matching)
        }
        return filter.numeric.map { (field, value) ->
            forField(field, t.FROM_VALUE.isNull.or(t.FROM_VALUE.le(value)).and(t.TO_VALUE.isNull.or(t.TO_VALUE.ge(value))))
        } + filter.values.map { (field, value) ->
            forField(field, DSL.lower(t.VALUE_EN).eq(value.lowercase()))
        }
    }
}
