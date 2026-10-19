package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
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

    // 모집 기간(app)·활동 기간(act) 일정과 열린 활동 규칙은 OpenActivityConditions가 가진다(GET /v1/activities?open=true와 공유)
    private val app = OpenActivityConditions.app
    private val act = OpenActivityConditions.act
    private val appJoin = OpenActivityConditions.appJoin
    private val actJoin = OpenActivityConditions.actJoin
    private val deadline = OpenActivityConditions.deadline

    // 열린 활동 + 유형 칩·종류·대상자 1차 판정(terms)
    private fun openConditions(condition: ProgramSummarySearch, now: OffsetDateTime): List<Condition> = buildList {
        add(OpenActivityConditions.open(now))
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
        val isOpen = OpenActivityConditions.open(now)
        // 유형·종류·대상자 1차 판정은 활동 행 자체를 거른다(열린 활동 수·최근 활동 모두 이 활동들로 센다)
        val activityFilter = buildList {
            add(ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name))
            condition.participationType?.let { add(ACTIVITIES.PARTICIPATION_TYPE.eq(it.name)) }
            condition.categories?.let { add(ACTIVITIES.CATEGORY.`in`(it.map(ActivityCategory::name))) }
            addAll(EligibilityRows.conditions(condition.eligibility))
        }
        val activityMatches = q?.let { ACTIVITIES.NAME.containsIgnoreCase(it).or(ACTIVITIES.NAME_EN.containsIgnoreCase(it)) }
        val openCount = DSL.count(DSL.`when`(isOpen, ACTIVITIES.ID))
        val nearestEnd = DSL.min(DSL.`when`(isOpen, deadline))
        val latestStart = DSL.max(OpenActivityConditions.activityStart)
        // 검색어가 활동 이름에 맞은 열린 활동 id. 활동 이름으로만 맞은 프로그램도 목록에 넣기 위해 HAVING에서도 쓴다
        val matchedIds = activityMatches?.let { DSL.arrayAggDistinct(DSL.`when`(it.and(isOpen), ACTIVITIES.ID)) }
        val programMatches = q?.let {
            PROGRAMS.NAME.containsIgnoreCase(it).or(PROGRAMS.NAME_EN.containsIgnoreCase(it))
                .or(ORGANIZATIONS.NAME.containsIgnoreCase(it)).or(ORGANIZATIONS.NAME_EN.containsIgnoreCase(it))
        }
        // 모집 중만 보기가 꺼지면 최근 2년 안에 활동이 있었던 프로그램(활동 시작·모집 마감·생성 시각 중 하나가 2년 안)까지(native-v1.md "모집 중인 프로그램만 보기")
        val cutoff = now.minusYears(RECENT_YEARS)
        val recent = OpenActivityConditions.activityStart.ge(cutoff).or(deadline.ge(cutoff)).or(ACTIVITIES.CREATED_AT.ge(cutoff.toLocalDateTime()))
        val select = dsl.select(
            PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN,
            ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN,
            openCount, nearestEnd, matchedIds ?: DSL.inline(null, Array<Long>::class.java), PROGRAMS.CATEGORY, latestStart,
        )
            .from(PROGRAMS)
            .join(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(PROGRAMS.ORGANIZATION_ID))
            .join(ACTIVITIES).on(ACTIVITIES.PROGRAM_ID.eq(PROGRAMS.ID))
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(activityFilter)
            .and(if (condition.openOnly) isOpen else recent)
            .groupBy(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN, PROGRAMS.CATEGORY, ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN)
        val filtered = if (programMatches != null && activityMatches != null) {
            select.having(DSL.condition(DSL.boolOr(programMatches)).or(DSL.condition(DSL.boolOr(activityMatches))))
        } else {
            select
        }
        // 열린 프로그램 먼저(가장 이른 마감 순), 그 뒤는 최근 활동 시작이 늦은 순
        return filtered
            .orderBy(openCount.gt(0).desc(), nearestEnd.asc().nullsLast(), latestStart.desc().nullsLast(), PROGRAMS.NAME.asc(), PROGRAMS.ID.asc())
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
                    latestActivityAt = row.value11(),
                )
            }
    }


    private companion object {
        const val RECENT_YEARS = 2L
    }
}
