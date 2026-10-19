package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.program.OpenActivity
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryRepository
import io.wid.dearby.application.program.ProgramSummarySearch
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.PublicationStatus
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqProgramSummaryRepository(private val dsl: DSLContext) : ProgramSummaryRepository {

    // 열린 활동: 게시 + 모집 기간(APPLICATION 시각 일정)이 지금을 포함. 유형 칩이 있으면 그 유형만. 대상자 필터는 1차 판정(terms)만 여기서
    private fun openConditions(condition: ProgramSummarySearch, now: OffsetDateTime): List<Condition> = buildList {
        add(ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name))
        add(SCHEDULES.SLOT.eq(SLOT_APPLICATION))
        add(SCHEDULES.TIME_TYPE.eq(TIME_TIMED))
        add(SCHEDULES.START_AT.isNull.or(SCHEDULES.START_AT.le(now)))
        add(SCHEDULES.END_AT.isNull.or(SCHEDULES.END_AT.gt(now)))
        condition.participationType?.let { add(ACTIVITIES.PARTICIPATION_TYPE.eq(it.name)) }
        condition.categories?.let { add(ACTIVITIES.CATEGORY.`in`(it.map(ActivityCategory::name))) }
        addAll(EligibilityRows.conditions(condition.eligibility))
    }

    override fun openActivities(condition: ProgramSummarySearch, now: OffsetDateTime): List<OpenActivity> =
        dsl.select(ACTIVITIES.ID, ACTIVITIES.PROGRAM_ID, ACTIVITIES.NAME, ACTIVITIES.NAME_EN, SCHEDULES.END_AT, ACTIVITIES.ELIGIBILITY)
            .from(ACTIVITIES)
            .join(SCHEDULES).on(SCHEDULES.ACTIVITY_ID.eq(ACTIVITIES.ID))
            .where(openConditions(condition, now))
            .orderBy(ACTIVITIES.ID)
            .fetch { row -> OpenActivity(row.value1()!!, row.value2()!!, row.value3()!!, row.value4(), row.value5(), EligibilityRows.read(row.value6())) }

    override fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary> {
        val q = condition.q?.trim()?.takeIf { it.isNotBlank() }
        val open = openConditions(condition, now)
        val activityMatches = q?.let { ACTIVITIES.NAME.containsIgnoreCase(it).or(ACTIVITIES.NAME_EN.containsIgnoreCase(it)) }
        val openCount = DSL.count(ACTIVITIES.ID)
        val nearestEnd = DSL.min(SCHEDULES.END_AT)
        // 검색어가 활동 이름에 맞은 열린 활동 id. 활동 이름으로만 맞은 프로그램도 목록에 넣기 위해 HAVING에서도 쓴다
        val matchedIds = activityMatches?.let { DSL.arrayAggDistinct(DSL.`when`(it, ACTIVITIES.ID)) }
        val programMatches = q?.let {
            PROGRAMS.NAME.containsIgnoreCase(it).or(PROGRAMS.NAME_EN.containsIgnoreCase(it))
                .or(ORGANIZATIONS.NAME.containsIgnoreCase(it)).or(ORGANIZATIONS.NAME_EN.containsIgnoreCase(it))
        }
        val select = dsl.select(
            PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN,
            ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN,
            openCount, nearestEnd, matchedIds ?: DSL.inline(null, Array<Long>::class.java),
        )
            .from(PROGRAMS)
            .join(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(PROGRAMS.ORGANIZATION_ID))
            .join(ACTIVITIES).on(ACTIVITIES.PROGRAM_ID.eq(PROGRAMS.ID))
            .join(SCHEDULES).on(SCHEDULES.ACTIVITY_ID.eq(ACTIVITIES.ID))
            .where(open)
            .groupBy(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN, ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN)
        val filtered = if (programMatches != null && activityMatches != null) {
            select.having(DSL.condition(DSL.boolOr(programMatches)).or(DSL.condition(DSL.boolOr(activityMatches))))
        } else {
            select
        }
        return filtered
            .orderBy(nearestEnd.asc().nullsLast(), PROGRAMS.NAME.asc(), PROGRAMS.ID.asc())
            .limit(condition.size)
            .offset(condition.cursor)
            .fetch { row ->
                ProgramSummary(
                    id = row.value1()!!,
                    name = row.value2()!!,
                    nameEn = row.value3(),
                    organizationId = row.value4()!!,
                    organizationName = row.value5()!!,
                    organizationNameEn = row.value6(),
                    openActivityCount = row.value7(),
                    nearestRecruitmentEndAt = row.value8(),
                    matchedActivityIds = row.value9()?.filterNotNull()?.sorted().orEmpty(),
                )
            }
    }

    private companion object {
        const val SLOT_APPLICATION = "APPLICATION"
        const val TIME_TIMED = "TIMED"
    }
}
