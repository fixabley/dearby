package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.application.program.OpenActivity
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryRepository
import io.wid.dearby.application.program.ProgramSummarySearch
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.ActivityStatus
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.TagKind
import io.wid.dearby.application.program.ProgramTags
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
        add(condition.statuses?.let { selected(it, now) } ?: OpenActivityConditions.open(now))
        condition.participationType?.let { add(ACTIVITIES.PARTICIPATION_TYPE.eq(it.name)) }
        condition.categories?.let { add(ACTIVITIES.CATEGORY.`in`(it.map(ActivityCategory::name))) }
        condition.kind?.let { add(OpenActivityConditions.kind(it)) }
        condition.tagIds?.let { addAll(TagRows.activityHasAllTags(it)) }
        addAll(EligibilityRows.conditions(condition.eligibility))
    }

    override fun openActivities(condition: ProgramSummarySearch, now: OffsetDateTime): List<OpenActivity> =
        dsl.select(
            ACTIVITIES.ID, ACTIVITIES.PROGRAM_ID, ACTIVITIES.NAME, ACTIVITIES.NAME_EN, deadline, ACTIVITIES.ELIGIBILITY, OpenActivityConditions.recruitmentEnd,
            DSL.field(TimetableRows.activityHasTimetable), OpenActivityConditions.recruitmentStart, OpenActivityConditions.status(now),
        )
            .from(ACTIVITIES)
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(openConditions(condition, now))
            .orderBy(ACTIVITIES.ID)
            .fetch { row ->
                OpenActivity(
                    row.value1()!!, row.value2()!!, row.value3()!!, row.value4(), row.value5(), row.value7(), row.value9(),
                    ActivityStatus.valueOf(row.value10()), EligibilityRows.read(row.value6()), row.value8() == true,
                )
            }

    // 상태 필터: 그 상태의 게시 활동. 모집 중·예정이 아닌 상태는 최근 3년 안(openOnly=false와 같은 창)
    private fun selected(statuses: Set<ActivityStatus>, now: OffsetDateTime): Condition =
        OpenActivityConditions.statusIn(statuses, now).and(OpenActivityConditions.open(now).or(OpenActivityConditions.upcoming(now)).or(recent(now)))

    override fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary> {
        val q = condition.q?.trim()?.takeIf { it.isNotBlank() }
        val isOpen = OpenActivityConditions.open(now)
        // 모집 예정(마감 숨기기, 2026-10-19 밤): 켜진 토글은 마감된 활동만 숨기므로 모집 예정 활동이 있는 프로그램도 돌려준다
        val isUpcoming = OpenActivityConditions.upcoming(now)
        val isOpenOrUpcoming = isOpen.or(isUpcoming)
        // 상태 필터가 있으면 그 상태의 활동, 없으면 모집 중·모집 예정(시간표 배지·검색어 일치의 기준)
        val isSelected = condition.statuses?.let { selected(it, now) } ?: isOpenOrUpcoming
        // 유형·종류·대상자 1차 판정은 활동 행 자체를 거른다(열린 활동 수·최근 활동 모두 이 활동들로 센다)
        val activityFilter = buildList {
            add(ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name))
            condition.participationType?.let { add(ACTIVITIES.PARTICIPATION_TYPE.eq(it.name)) }
            condition.categories?.let { add(ACTIVITIES.CATEGORY.`in`(it.map(ActivityCategory::name))) }
            condition.kind?.let { add(OpenActivityConditions.kind(it)) }
            condition.tagIds?.let { addAll(TagRows.activityHasAllTags(it)) }
            addAll(EligibilityRows.conditions(condition.eligibility))
        }
        // 검색어는 활동 이름과 태그 이름(한글·영문)에 맞는다(native-v1.md "태그")
        val activityMatches = q?.let { ACTIVITIES.NAME.containsIgnoreCase(it).or(ACTIVITIES.NAME_EN.containsIgnoreCase(it)).or(TagRows.activityTagMatches(it)) }
        val openCount = DSL.count(DSL.`when`(isOpen, ACTIVITIES.ID))
        val nearestEnd = DSL.min(DSL.`when`(isOpen, deadline))
        val nearestRealEnd = DSL.min(DSL.`when`(isOpen, OpenActivityConditions.recruitmentEnd))
        val upcomingCount = DSL.count(DSL.`when`(isUpcoming, ACTIVITIES.ID))
        val nearestStart = DSL.min(DSL.`when`(isUpcoming, OpenActivityConditions.recruitmentStart))
        val latestStart = DSL.max(OpenActivityConditions.activityStart)
        // 접힌 카드 "시간표" 배지: 모집 중·모집 예정 활동 중 시간표 있는 것이 있는지
        val hasTimetable = DSL.boolOr(isSelected.and(TimetableRows.activityHasTimetable))
        val statuses = DSL.arrayAgg(OpenActivityConditions.status(now))
        // 검색어가 활동 이름에 맞은 모집 중·모집 예정 활동 id. 활동 이름으로만 맞은 프로그램도 목록에 넣기 위해 HAVING에서도 쓴다
        val matchedIds = activityMatches?.let { DSL.arrayAggDistinct(DSL.`when`(it.and(isSelected), ACTIVITIES.ID)) }
        val programMatches = q?.let {
            PROGRAMS.NAME.containsIgnoreCase(it).or(PROGRAMS.NAME_EN.containsIgnoreCase(it))
                .or(ORGANIZATIONS.NAME.containsIgnoreCase(it)).or(ORGANIZATIONS.NAME_EN.containsIgnoreCase(it))
        }
        // 모집 중만 보기가 꺼지면 최근 3년 안에 활동이 있었던 프로그램(활동 시작·모집 마감·생성 시각 중 하나가 3년 안)까지(2026-10-19 저녁 2년 → 3년)(native-v1.md "모집 중인 프로그램만 보기")
        val recent = recent(now)
        val select = dsl.select(
            PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN,
            ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN,
            openCount, nearestEnd, matchedIds ?: DSL.inline(null, Array<Long>::class.java), PROGRAMS.CATEGORY, latestStart,
            upcomingCount, nearestStart, nearestRealEnd, hasTimetable, statuses,
        )
            .from(PROGRAMS)
            .join(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(PROGRAMS.ORGANIZATION_ID))
            .join(ACTIVITIES).on(ACTIVITIES.PROGRAM_ID.eq(PROGRAMS.ID))
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(activityFilter)
            .and(condition.programIds?.let { PROGRAMS.ID.`in`(it) } ?: condition.statuses?.let { isSelected } ?: if (condition.openOnly) isOpenOrUpcoming else recent)
            .groupBy(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.NAME_EN, PROGRAMS.CATEGORY, ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS.NAME_EN)
        val filtered = if (programMatches != null && activityMatches != null) {
            select.having(DSL.condition(DSL.boolOr(programMatches)).or(DSL.condition(DSL.boolOr(activityMatches))))
        } else {
            select
        }
        // 모집 중 프로그램 먼저(가장 이른 마감 순) → 모집 예정 프로그램(가장 이른 시작 순) → 나머지는 최근 활동 시작이 늦은 순
        return filtered
            .orderBy(
                openCount.gt(0).desc(), nearestEnd.asc().nullsLast(),
                upcomingCount.gt(0).desc(), nearestStart.asc().nullsLast(),
                latestStart.desc().nullsLast(), PROGRAMS.NAME.asc(), PROGRAMS.ID.asc(),
            )
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
                    nearestRecruitmentEndAt = row.value14(), // 모집 마감만. 정렬(nearestEnd)은 활동 시작 대체가 있는 정렬 키 그대로
                    matchedActivityIds = row.value9()?.filterNotNull()?.sorted().orEmpty(),
                    category = row.value10()?.let(ActivityCategory::valueOf),
                    latestActivityAt = row.value11(),
                    upcomingActivityCount = row.value12(),
                    nearestRecruitmentStartAt = row.value13(),
                    hasTimetable = row.value15() == true,
                    statusCounts = row.value16().orEmpty().filterNotNull().groupingBy(ActivityStatus::valueOf).eachCount(),
                )
            }
    }


    // 최근 3년 안의 활동(활동 시작·모집 마감·생성 시각 중 하나가 cutoff 이후). 묶음 목록(openOnly=false)과 태그 합집합이 같은 창을 쓴다
    private fun recent(now: OffsetDateTime): Condition {
        val cutoff = now.minusYears(RECENT_YEARS)
        return OpenActivityConditions.activityStart.ge(cutoff).or(deadline.ge(cutoff)).or(ACTIVITIES.CREATED_AT.ge(cutoff.toLocalDateTime()))
    }

    // 묶음 머리의 태그(native-v1.md "태그", "분야·성격 태그와 아이콘"). 활동 쪽은 최근 3년 안의 게시 활동(모집 상태와 무관, 2026-10-20: 마감된 프로그램도 태그가 보이게)
    // tags: 프로그램 태그가 있으면 그것, 없으면 활동 태그의 합집합(최대 MAX_SUMMARY_TAGS개), 사전 순
    // fieldTags: 프로그램 분야 태그(없으면 활동 분야 태그 합집합)를 활동 연관도 평균 내림차순 → position → 사전 순. traitTags: 프로그램 성격 태그(없으면 활동 합집합), 사전 순
    override fun tagsByProgram(programIds: Collection<Id>, now: OffsetDateTime): Map<Id, ProgramTags> {
        if (programIds.isEmpty()) return emptyMap()
        val own = TagRows.programRows(dsl, programIds)
        val relevance = DSL.avg(ACTIVITY_TAGS.RELEVANCE)
        val fromActivities = dsl.select(TagRows.FIELDS + ACTIVITIES.PROGRAM_ID + relevance)
            .from(ACTIVITIES)
            .join(ACTIVITY_TAGS).on(ACTIVITY_TAGS.ACTIVITY_ID.eq(ACTIVITIES.ID))
            .join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .leftJoin(app).on(appJoin)
            .leftJoin(act).on(actJoin)
            .where(ACTIVITIES.PROGRAM_ID.`in`(programIds), ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name), recent(now))
            .groupBy(TagRows.FIELDS + ACTIVITIES.PROGRAM_ID + TAGS.SORT_ORDER)
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch()
            .groupBy({ it[ACTIVITIES.PROGRAM_ID]!! }) { TagRows.tag(it) to it[relevance]?.toDouble() }
        return programIds.associateWith { id ->
            val mine = own[id].orEmpty()
            val theirs = fromActivities[id].orEmpty()
            val averages = theirs.associate { (tag, average) -> tag.id to average }
            val ownFields = mine.filter { it.first.kind == TagKind.FIELD }
            val fields = if (ownFields.isNotEmpty()) ownFields.map { (tag, position) -> TagRows.RankedField(tag, averages[tag.id], position) }
            else theirs.filter { it.first.kind == TagKind.FIELD }.map { (tag, average) -> TagRows.RankedField(tag, average, null) }
            ProgramTags(
                tags = mine.map { it.first }.ifEmpty { theirs.map { it.first }.take(MAX_SUMMARY_TAGS) },
                fieldTags = TagRows.orderFields(fields),
                traitTags = mine.map { it.first }.filter { it.kind == TagKind.TRAIT }.ifEmpty { theirs.map { it.first }.filter { it.kind == TagKind.TRAIT } },
            )
        }
    }

    private companion object {
        const val RECENT_YEARS = 3L
        const val MAX_SUMMARY_TAGS = 6
    }
}
