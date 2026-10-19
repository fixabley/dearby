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
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqProgramSummaryRepository(private val dsl: DSLContext) : ProgramSummaryRepository {

    // 모집 기간(APPLICATION 시각 일정)과 활동 기간(ACTIVITY 슬롯)을 활동마다 하나씩 붙인다(unique (activity_id, slot))
    private val app = SCHEDULES.`as`("app")
    private val act = SCHEDULES.`as`("act")
    private val appJoin: Condition = app.ACTIVITY_ID.eq(ACTIVITIES.ID).and(app.SLOT.eq(SLOT_APPLICATION)).and(app.TIME_TYPE.eq(TIME_TIMED))
    private val actJoin: Condition = act.ACTIVITY_ID.eq(ACTIVITIES.ID).and(act.SLOT.eq(SLOT_ACTIVITY))
    // 활동 시작 시각: 시각 일정이면 start_at, 종일이면 start_date의 서울 0시
    private val activityStart: Field<OffsetDateTime?> =
        DSL.coalesce(act.START_AT, DSL.field("({0}::timestamp at time zone 'Asia/Seoul')", OffsetDateTime::class.java, act.START_DATE))
    // 정렬 키(admin-v1.md "정렬 기준 보완", 2026-10-19): 모집 마감 → 없으면 활동 시작 → 둘 다 없으면 null(맨 뒤)
    private val deadline: Field<OffsetDateTime?> = DSL.coalesce(app.END_AT, activityStart)

    // 열린 활동: 게시 + (모집 기간이 있으면 지금을 포함) 또는 (모집 기간이 없으면 활동 시작이 아직 오지 않음 — 활동 시작을 모집 마감으로 본다).
    // 유형 칩이 있으면 그 유형만. 대상자 필터는 1차 판정(terms)만 여기서
    private fun openConditions(condition: ProgramSummarySearch, now: OffsetDateTime): List<Condition> = buildList {
        add(ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name))
        val recruiting = app.ID.isNotNull
            .and(app.START_AT.isNull.or(app.START_AT.le(now)))
            .and(app.END_AT.isNull.or(app.END_AT.gt(now)))
        val upcoming = app.ID.isNull.and(activityStart.gt(now))
        add(recruiting.or(upcoming))
        condition.participationType?.let { add(ACTIVITIES.PARTICIPATION_TYPE.eq(it.name)) }
        condition.categories?.let { add(ACTIVITIES.CATEGORY.`in`(it.map(ActivityCategory::name))) }
        addAll(EligibilityRows.conditions(condition.eligibility))
    }

    override fun openActivities(condition: ProgramSummarySearch, now: OffsetDateTime): List<OpenActivity> =
        dsl.select(ACTIVITIES.ID, ACTIVITIES.PROGRAM_ID, ACTIVITIES.NAME, ACTIVITIES.NAME_EN, deadline, ACTIVITIES.ELIGIBILITY)
            .from(ACTIVITIES)
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(openConditions(condition, now))
            .orderBy(ACTIVITIES.ID)
            .fetch { row -> OpenActivity(row.value1()!!, row.value2()!!, row.value3()!!, row.value4(), row.value5(), EligibilityRows.read(row.value6())) }

    override fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary> {
        val q = condition.q?.trim()?.takeIf { it.isNotBlank() }
        val open = openConditions(condition, now)
        val activityMatches = q?.let { ACTIVITIES.NAME.containsIgnoreCase(it).or(ACTIVITIES.NAME_EN.containsIgnoreCase(it)) }
        val openCount = DSL.count(ACTIVITIES.ID)
        val nearestEnd = DSL.min(deadline)
        // 검색어가 활동 이름에 맞은 열린 활동 id. 활동 이름으로만 맞은 프로그램도 목록에 넣기 위해 HAVING에서도 쓴다
        val matchedIds = activityMatches?.let { DSL.arrayAggDistinct(DSL.`when`(it, ACTIVITIES.ID)) }
        val programMatches = q?.let {
            PROGRAMS.NAME.containsIgnoreCase(it).or(PROGRAMS.NAME_EN.containsIgnoreCase(it))
                .or(ORGANIZATIONS.NAME.containsIgnoreCase(it)).or(ORGANIZATIONS.NAME_EN.containsIgnoreCase(it))
        }
        val select = dsl.select(
            PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN,
            ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN,
            openCount, nearestEnd, matchedIds ?: DSL.inline(null, Array<Long>::class.java), PROGRAMS.CATEGORY,
        )
            .from(PROGRAMS)
            .join(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(PROGRAMS.ORGANIZATION_ID))
            .join(ACTIVITIES).on(ACTIVITIES.PROGRAM_ID.eq(PROGRAMS.ID))
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(open)
            .groupBy(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN, PROGRAMS.CATEGORY, ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN)
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
                    category = row.value10()?.let(ActivityCategory::valueOf),
                )
            }
    }

    private companion object {
        const val SLOT_APPLICATION = "APPLICATION"
        const val SLOT_ACTIVITY = "ACTIVITY"
        const val TIME_TIMED = "TIMED"
    }
}
