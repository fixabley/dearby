package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.collection.StorylessActivity
import io.wid.dearby.domain.*
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.DraftSaveResult
import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.curation.Freshness
import io.wid.dearby.domain.curation.RecruitmentStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Repository
class JooqActivityDraftRepository(private val dsl: DSLContext) : ActivityDraftRepository {

    @Transactional
    override fun saveDraft(programId: Id, sourceId: UUID, activity: CollectedActivity): DraftSaveResult {
        if (dsl.fetchExists(DISMISSED_ACTIVITY_SOURCES, DISMISSED_ACTIVITY_SOURCES.SOURCE_ID.eq(sourceId))) {
            return DraftSaveResult.SKIPPED_DISMISSED
        }
        val existing = dsl.select(
            ACTIVITIES.ID, ACTIVITIES.PUBLICATION_STATUS, ACTIVITIES.NAME, ACTIVITIES.NAME_EN, ACTIVITIES.DESCRIPTION, ACTIVITIES.DESCRIPTION_EN,
            ACTIVITIES.AUDIENCE, ACTIVITIES.ELIGIBILITY, ACTIVITIES.PARTICIPATION_TYPE, ACTIVITIES.INHERITED_FIELDS, ACTIVITIES.CURATED_FIELDS,
            // 운영자 지정으로 보호할 수 있는 나머지 열(없는 열을 읽으면 jOOQ가 예외를 던진다)
            ACTIVITIES.OFFICIAL_URL, ACTIVITIES.APPLICATION_URL, ACTIVITIES.LOCATION, ACTIVITIES.COST, ACTIVITIES.CATEGORY,
            ACTIVITIES.ELIGIBILITY_CHECKED, ACTIVITIES.HOOK, ACTIVITIES.HIGHLIGHTS,
        )
            .from(ACTIVITIES)
            .where(ACTIVITIES.SOURCE_ID.eq(sourceId))
            .forUpdate()
            .fetchOne()
        if (existing?.get(ACTIVITIES.PUBLICATION_STATUS) == PublicationStatus.PUBLISHED.name) {
            // 게시 활동은 덮어쓰지 않되 비어 있는 대기열 글만 채운다(2026-10-25: 게시 활동에 hook이 하나도 없던 문제)
            fillStory(existing.get(ACTIVITIES.ID)!!, activity.hook, activity.highlights)
            return DraftSaveResult.SKIPPED_PUBLISHED
        }

        // 운영자 지정 필드(admin-v1.md "이전 기수 정보 자동완성" 보완): curated_fields에 있는 필드만 기존 값을 지키고, 나머지는 수집값으로 갱신한다.
        // 이전 기수 자동완성: 수집값이 비어 있는 대상자 조건·대상 원문·참여 방식은 같은 프로그램의 이전 기수에서 가져오고 inherited_fields에 남긴다.
        // 수집값이 있으면 가져온 표시를 뺀다
        val curated = CuratedFields.read(existing?.get(ACTIVITIES.CURATED_FIELDS))
        val previous = findPreviousEdition(programId, excluding = existing?.get(ACTIVITIES.ID))
        val inherited = mutableListOf<String>()
        fun <T : Any> keepOrCollected(field: String, current: T?, collected: T): T = if (field in curated) current ?: collected else collected
        fun <T : Any> resolve(field: String, current: T?, collected: T?, fromPrevious: T?): T? {
            if (field in curated) return current
            if (collected != null) return collected
            if (fromPrevious != null) inherited += field
            return fromPrevious
        }
        val name = keepOrCollected(CuratedFields.NAME, existing?.get(ACTIVITIES.NAME), activity.name)
        val nameEn = if (CuratedFields.NAME_EN in curated) existing?.get(ACTIVITIES.NAME_EN) else activity.nameEn
        val description = keepOrCollected(CuratedFields.DESCRIPTION, existing?.get(ACTIVITIES.DESCRIPTION), activity.description)
        val descriptionEn = if (CuratedFields.DESCRIPTION_EN in curated) existing?.get(ACTIVITIES.DESCRIPTION_EN) else activity.descriptionEn
        // 어드민이 고친 공식 안내·신청 링크도 보호한다(admin-v1.md "운영자 지정 필드", 2026-10-19)
        val officialUrl = if (CuratedFields.OFFICIAL_URL in curated) existing?.get(ACTIVITIES.OFFICIAL_URL) ?: activity.officialUrl else activity.officialUrl
        val applicationUrl = if (CuratedFields.APPLICATION_URL in curated) existing?.get(ACTIVITIES.APPLICATION_URL) else activity.applicationUrl
        val location = if (CuratedFields.LOCATION in curated) existing?.get(ACTIVITIES.LOCATION) else activity.location
        val cost = if (CuratedFields.COST in curated) existing?.get(ACTIVITIES.COST) else activity.cost
        // 대기열 한 줄 소개·핵심 줄(native-v1.md "맞춤 대기열"): 운영자가 고쳤으면 지키고, 아니면 이번 수집값(없으면 지운다)
        val hook = if (CuratedFields.HOOK in curated) existing?.get(ACTIVITIES.HOOK) else activity.hook
        val highlights = if (CuratedFields.HIGHLIGHTS in curated) existing?.get(ACTIVITIES.HIGHLIGHTS) else HighlightRows.jsonb(activity.highlights)
        // 종류: 모델이 못 뽑으면 프로그램 기본 종류(admin-v1.md "프로그램 기본 종류")
        val category = if (CuratedFields.CATEGORY in curated) existing?.get(ACTIVITIES.CATEGORY)
            else activity.category?.name ?: dsl.select(PROGRAMS.CATEGORY).from(PROGRAMS).where(PROGRAMS.ID.eq(programId)).fetchOne()?.value1()
        val eligibility = resolve(InheritedFields.ELIGIBILITY, EligibilityRows.read(existing?.get(ACTIVITIES.ELIGIBILITY)), activity.eligibility, previous?.eligibility)
        val audience = resolve(InheritedFields.AUDIENCE, existing?.get(ACTIVITIES.AUDIENCE)?.ifBlank { null }, activity.audience?.ifBlank { null }, previous?.audience?.ifBlank { null })
        // 지원 자격 확인(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"): 이번 원문에서 대상 구절(제한 없음 명시 포함)이나 조건을 읽었으면 true. 이전 기수에서 가져온 값은 확인이 아니고, 한 번 true면 내리지 않는다
        val eligibilityChecked = existing?.get(ACTIVITIES.ELIGIBILITY_CHECKED) == true || activity.eligibility != null || !activity.audience.isNullOrBlank()
        val participationType = resolve(
            InheritedFields.PARTICIPATION_TYPE,
            existing?.get(ACTIVITIES.PARTICIPATION_TYPE)?.let(ParticipationType::valueOf), activity.participationType, previous?.participationType,
        ) ?: ParticipationType.REGISTRATION

        val now = LocalDateTime.now()
        val activityId = existing?.get(ACTIVITIES.ID) ?: dsl.insertInto(ACTIVITIES)
            .set(ACTIVITIES.SOURCE_ID, sourceId)
            .set(ACTIVITIES.PROGRAM_ID, programId)
            .set(ACTIVITIES.NAME, name)
            .set(ACTIVITIES.DESCRIPTION, description)
            .set(ACTIVITIES.OFFICIAL_URL, activity.officialUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, participationType.name)
            .set(ACTIVITIES.RECRUITMENT_STATUS, RecruitmentStatus.UNKNOWN.name)
            .set(ACTIVITIES.FRESHNESS, Freshness.VERIFIED.name)
            .set(ACTIVITIES.SOURCE_NOTE, "")
            .set(ACTIVITIES.PUBLICATION_STATUS, PublicationStatus.DRAFT.name)
            .set(ACTIVITIES.CREATED_AT, now)
            .set(ACTIVITIES.UPDATED_AT, now)
            .returning(ACTIVITIES.ID)
            .fetchOne()!!.id!!
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.PROGRAM_ID, programId)
            .set(ACTIVITIES.NAME, name)
            .set(ACTIVITIES.NAME_EN, nameEn)
            .set(ACTIVITIES.DESCRIPTION, description)
            .set(ACTIVITIES.DESCRIPTION_EN, descriptionEn)
            .set(ACTIVITIES.OFFICIAL_URL, officialUrl)
            .set(ACTIVITIES.OFFICIAL_SOURCE, activity.officialSource)
            .set(ACTIVITIES.APPLICATION_URL, applicationUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, participationType.name)
            .set(ACTIVITIES.LOCATION, location)
            .set(ACTIVITIES.COST, cost)
            .set(ACTIVITIES.HOOK, hook)
            .set(ACTIVITIES.HIGHLIGHTS, highlights)
            .set(ACTIVITIES.CATEGORY, category)
            .set(ACTIVITIES.AUDIENCE, audience)
            .set(ACTIVITIES.ELIGIBILITY, EligibilityRows.jsonb(eligibility))
            .set(ACTIVITIES.ELIGIBILITY_CHECKED, eligibilityChecked)
            .set(ACTIVITIES.INHERITED_FIELDS, InheritedFields.jsonb(inherited))
            .set(ACTIVITIES.SOURCE_CHECKED_AT, activity.checkedAt)
            .set(ACTIVITIES.VALID_UNTIL, activity.checkedAt.plus(VERIFICATION_LIFETIME))
            .set(ACTIVITIES.FRESHNESS, Freshness.VERIFIED.name)
            .set(ACTIVITIES.UPDATED_AT, now)
            .where(ACTIVITIES.ID.eq(activityId))
            .execute()
        // 일정은 매번 수집 결과로 바꾼다. 단 운영자가 직접 지정한(curated) 활동 기간·모집 기간은 남긴다(admin-v1.md "일정 직접 지정")
        val curatedSlots = dsl.select(SCHEDULES.SLOT).from(SCHEDULES)
            .where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED.isTrue).fetch(SCHEDULES.SLOT).toSet()
        // 운영자가 만든·고친 세부 일정(curated)과 지운 일정(curated_deleted, admin-v1.md "세부 일정 편집기")은 남기고 수집 일정만 다시 쓴다.
        // 모집·활동 기간 행은 지우지 않고 제자리에서 고친다: id에 묶인 일정 상태·사용자 선택·겹침 확인 시간·장소가 남는다(native-v1.md "내 활동 서버 저장")
        dsl.deleteFrom(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.isNull, SCHEDULES.CURATED.isFalse, SCHEDULES.CURATED_DELETED.isFalse).execute()
        if (SLOT_ACTIVITY !in curatedSlots) {
            writeSchedule(activityId, SLOT_ACTIVITY, name, ScheduleKind.EVENT, null, null, activity.startDate, activity.endDate, activity.dateLabel, now)
            // 활동 기간의 반복(native-v1.md "반복 일정과 겹침 결과 묶음")은 그 행에 반복·겹침 확인 시간이 없을 때만 채운다(운영자가 둔 값을 덮지 않는다)
            activity.activityRecurrence?.let {
                dsl.update(SCHEDULES)
                    .set(SCHEDULES.RECURRENCE, it.rule.rule)
                    .set(SCHEDULES.OVERLAP_START_TIME, it.time.startTime)
                    .set(SCHEDULES.OVERLAP_END_TIME, it.time.endTime)
                    .where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(SLOT_ACTIVITY), SCHEDULES.RECURRENCE.isNull, SCHEDULES.OVERLAP_START_TIME.isNull)
                    .execute()
            }
        }
        // 수집 장소(native-v1.md "수집기 장소"): 운영자가 지정·지운 장소(curated place)는 두고, 활동 기간 행에 장소가 없을 때만 채운다.
        // 장소를 못 찾은 재수집은 있던 장소를 지우지 않는다(위 활동 기간 행 갱신은 장소 열을 건드리지 않는다)
        activity.place?.takeIf { CuratedFields.PLACE !in curated }?.let {
            dsl.update(SCHEDULES)
                .set(SCHEDULES.ADDRESS_LINE1, it.addressLine1)
                .set(SCHEDULES.ADDRESS_LINE2, it.addressLine2)
                .set(SCHEDULES.LAT, it.lat)
                .set(SCHEDULES.LNG, it.lng)
                .where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(SLOT_ACTIVITY), SCHEDULES.ADDRESS_LINE1.isNull)
                .execute()
        }
        val hasRecruitment = activity.recruitmentStartAt != null || activity.recruitmentEndAt != null || activity.recruitmentStartDate != null || activity.recruitmentEndDate != null
        if (SLOT_APPLICATION !in curatedSlots) {
            if (hasRecruitment) writeSchedule(
                activityId, SLOT_APPLICATION, "신청 기간", ScheduleKind.DEADLINE,
                activity.recruitmentStartAt, activity.recruitmentEndAt, activity.recruitmentStartDate, activity.recruitmentEndDate, "", now,
            ) else dsl.deleteFrom(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(SLOT_APPLICATION)).execute()
        }
        // 운영자 세부 일정이나 지운 일정과 제목(띄어쓰기 무시)·시작이 같은 수집 일정은 다시 넣지 않는다
        val kept = dsl.select(SCHEDULES.TITLE, SCHEDULES.START_AT, SCHEDULES.START_DATE).from(SCHEDULES)
            .where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.isNull, SCHEDULES.CURATED.isTrue.or(SCHEDULES.CURATED_DELETED.isTrue))
            .fetch { scheduleKey(it.value1()!!, it.value2(), it.value3()) }.toSet()
        activity.schedules.filter { scheduleKey(it.title, it.startAt, it.startDate) !in kept }.forEach {
            writeSchedule(activityId, null, it.title, it.kind, it.startAt, it.endAt, it.startDate, it.endDate, it.dateLabel, now, it.recurrence)
        }
        EligibilityRows.writeTerms(dsl, activityId, eligibility)
        // 태그(native-v1.md "태그"): 운영자가 정한 연결은 두고, 아니면 모델이 사전에서 고른 이름(모르는 이름은 버림), 비면 프로그램 태그(연관도 없음).
        // 분야 태그는 모델이 답한 연관도를 함께 쓴다(native-v1.md "분야·성격 태그와 아이콘")
        if (CuratedFields.TAGS !in curated) {
            fun key(name: String) = name.replace(Regex("\\s"), "").lowercase()
            val relevance = activity.fieldTags.mapKeys { key(it.key) }
            val wanted = (activity.tags + activity.fieldTags.keys).map(::key).filter { it.isNotEmpty() }.toSet()
            val fromModel = if (wanted.isEmpty()) emptyList() else dsl.select(TAGS.ID, TAGS.NAME, TAGS.NAME_EN, TAGS.KIND).from(TAGS)
                .where(DSL.lower(withoutSpaces(TAGS.NAME)).`in`(wanted).or(DSL.lower(withoutSpaces(TAGS.NAME_EN)).`in`(wanted)))
                .fetch { row ->
                    val score = if (row[TAGS.KIND] == TagKind.FIELD.name) (relevance[key(row[TAGS.NAME]!!)] ?: row[TAGS.NAME_EN]?.let { relevance[key(it)] }) else null
                    row[TAGS.ID]!! to score?.toShort() // 0~100은 ActivityVerifier가 맞춘다
                }
            val tagIds = fromModel.ifEmpty {
                dsl.select(PROGRAM_TAGS.TAG_ID).from(PROGRAM_TAGS).where(PROGRAM_TAGS.PROGRAM_ID.eq(programId)).fetch { it.value1()!! to null }
            }
            dsl.deleteFrom(ACTIVITY_TAGS).where(ACTIVITY_TAGS.ACTIVITY_ID.eq(activityId)).execute()
            tagIds.distinctBy { it.first }.forEach { (tagId, score) ->
                dsl.insertInto(ACTIVITY_TAGS).set(ACTIVITY_TAGS.ACTIVITY_ID, activityId).set(ACTIVITY_TAGS.TAG_ID, tagId).set(ACTIVITY_TAGS.RELEVANCE, score).execute()
            }
        }
        return DraftSaveResult.SAVED
    }

    override fun fillStory(activityId: Id, hook: String?, highlights: List<Highlight>): Boolean {
        fun notCurated(field: String) = DSL.condition("not (coalesce({0}, '[]'::jsonb) @> {1}::jsonb)", ACTIVITIES.CURATED_FIELDS, DSL.inline("[\"$field\"]"))
        val hookFilled = hook != null && dsl.update(ACTIVITIES).set(ACTIVITIES.HOOK, hook)
            .where(ACTIVITIES.ID.eq(activityId), ACTIVITIES.HOOK.isNull, notCurated(CuratedFields.HOOK)).execute() > 0
        val highlightsFilled = highlights.isNotEmpty() && dsl.update(ACTIVITIES).set(ACTIVITIES.HIGHLIGHTS, HighlightRows.jsonb(highlights))
            .where(ACTIVITIES.ID.eq(activityId), ACTIVITIES.HIGHLIGHTS.isNull, notCurated(CuratedFields.HIGHLIGHTS)).execute() > 0
        return hookFilled || highlightsFilled
    }

    override fun findStoryless(): List<StorylessActivity> =
        dsl.select(ACTIVITIES.ID, ACTIVITIES.NAME, ACTIVITIES.DESCRIPTION).from(ACTIVITIES)
            .where(
                ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name), ACTIVITIES.HOOK.isNull,
                DSL.condition("not (coalesce({0}, '[]'::jsonb) @> {1}::jsonb)", ACTIVITIES.CURATED_FIELDS, DSL.inline("[\"${CuratedFields.HOOK}\"]")),
            )
            .orderBy(ACTIVITIES.ID)
            .fetch { StorylessActivity(it.value1()!!, it.value2()!!, it.value3()!!) }

    override fun findPreviousEdition(programId: Id): PreviousEdition? = findPreviousEdition(programId, excluding = null)


    // 같은 프로그램의 다른 활동 중 게시된 것을 먼저, 그다음 가장 최근에 만들어진 것
    private fun findPreviousEdition(programId: Id, excluding: Id?): PreviousEdition? =
        dsl.select(ACTIVITIES.NAME, ACTIVITIES.AUDIENCE, ACTIVITIES.ELIGIBILITY, ACTIVITIES.PARTICIPATION_TYPE)
            .from(ACTIVITIES)
            .where(ACTIVITIES.PROGRAM_ID.eq(programId), excluding?.let { ACTIVITIES.ID.ne(it) } ?: DSL.noCondition())
            .orderBy(
                DSL.`when`(ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name), 0).otherwise(1),
                ACTIVITIES.CREATED_AT.desc(),
                ACTIVITIES.ID.desc(),
            )
            .limit(1)
            .fetchOne()
            ?.let {
                PreviousEdition(
                    name = it[ACTIVITIES.NAME]!!,
                    audience = it[ACTIVITIES.AUDIENCE]?.ifBlank { null },
                    eligibility = EligibilityRows.read(it[ACTIVITIES.ELIGIBILITY]),
                    participationType = ParticipationType.valueOf(it[ACTIVITIES.PARTICIPATION_TYPE]!!),
                )
            }

    // 시각이 있으면 시각 일정, 날짜만 있으면 종일 일정(끝 날짜는 포함이라 하루 뒤를 exclusive로), 둘 다 없으면 dateLabel만
    private fun scheduleKey(title: String, startAt: OffsetDateTime?, startDate: LocalDate?) =
        Triple(title.replace(Regex("\\s"), "").lowercase(), startAt?.toInstant(), startDate)

    // 슬롯 일정은 있으면 그 행을 고치고(id 유지), 없거나 세부 일정이면 새로 넣는다
    private fun writeSchedule(
        activityId: Id,
        slot: String?,
        title: String,
        kind: ScheduleKind,
        startAt: OffsetDateTime?,
        endAt: OffsetDateTime?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        dateLabel: String,
        now: LocalDateTime,
        recurrence: Recurrence? = null, // 세부 일정만. 슬롯 행의 반복은 운영자 값이 남게 따로 채운다
    ) {
        val timed = startAt != null || endAt != null
        val allDay = !timed && (startDate != null || endDate != null)
        val start = startDate ?: endDate
        val end = endDate ?: startDate
        val existing = slot?.let {
            dsl.select(SCHEDULES.ID).from(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(it)).fetchOne(SCHEDULES.ID)
        }
        val values = mapOf(
            SCHEDULES.TITLE to title,
            SCHEDULES.KIND to kind.name,
            SCHEDULES.STATUS to ScheduleStatus.CONFIRMED.name,
            SCHEDULES.TIME_TYPE to when { timed -> TIME_TIMED; allDay -> TIME_ALL_DAY; else -> null },
            SCHEDULES.START_AT to startAt,
            SCHEDULES.END_AT to endAt,
            SCHEDULES.TIME_ZONE to if (timed) TIME_ZONE else null,
            SCHEDULES.START_DATE to if (allDay) start else null,
            SCHEDULES.END_DATE_EXCLUSIVE to if (allDay) end!!.plusDays(1) else null,
            SCHEDULES.DATE_LABEL to dateLabel,
            SCHEDULES.UPDATED_AT to now,
        )
        if (existing != null) {
            dsl.update(SCHEDULES).set(values).set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1)).where(SCHEDULES.ID.eq(existing)).execute()
            return
        }
        dsl.insertInto(SCHEDULES)
            .set(values)
            .set(SCHEDULES.RECURRENCE, recurrence?.rule)
            .set(SCHEDULES.ACTIVITY_ID, activityId)
            .set(SCHEDULES.SLOT, slot)
            .set(SCHEDULES.SEQUENCE, 0)
            .set(SCHEDULES.CREATED_AT, now)
            .execute()
    }

    private companion object {
        const val SLOT_APPLICATION = "APPLICATION"
        const val SLOT_ACTIVITY = "ACTIVITY"
        const val TIME_TIMED = "TIMED"
        const val TIME_ALL_DAY = "ALL_DAY"
        const val TIME_ZONE = "Asia/Seoul" // 한국 행사만 수집한다
        val VERIFICATION_LIFETIME: Duration = Duration.ofHours(24) // 확인 내용이 유효한 기간(기존 수집기와 같음)
    }
}
