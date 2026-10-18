package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.collection.ActivityDraftRepository
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
        )
            .from(ACTIVITIES)
            .where(ACTIVITIES.SOURCE_ID.eq(sourceId))
            .forUpdate()
            .fetchOne()
        if (existing?.get(ACTIVITIES.PUBLICATION_STATUS) == PublicationStatus.PUBLISHED.name) return DraftSaveResult.SKIPPED_PUBLISHED

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
        val eligibility = resolve(InheritedFields.ELIGIBILITY, EligibilityRows.read(existing?.get(ACTIVITIES.ELIGIBILITY)), activity.eligibility, previous?.eligibility)
        val audience = resolve(InheritedFields.AUDIENCE, existing?.get(ACTIVITIES.AUDIENCE)?.ifBlank { null }, activity.audience?.ifBlank { null }, previous?.audience?.ifBlank { null })
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
            .set(ACTIVITIES.OFFICIAL_URL, activity.officialUrl)
            .set(ACTIVITIES.OFFICIAL_SOURCE, activity.officialSource)
            .set(ACTIVITIES.APPLICATION_URL, activity.applicationUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, participationType.name)
            .set(ACTIVITIES.LOCATION, activity.location)
            .set(ACTIVITIES.COST, activity.cost)
            .set(ACTIVITIES.AUDIENCE, audience)
            .set(ACTIVITIES.ELIGIBILITY, EligibilityRows.jsonb(eligibility))
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
        dsl.deleteFrom(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED.isFalse).execute()
        if (SLOT_ACTIVITY !in curatedSlots) {
            insertSchedule(activityId, SLOT_ACTIVITY, name, ScheduleKind.EVENT, null, null, activity.startDate, activity.endDate, activity.dateLabel, now)
        }
        val hasRecruitment = activity.recruitmentStartAt != null || activity.recruitmentEndAt != null || activity.recruitmentStartDate != null || activity.recruitmentEndDate != null
        if (SLOT_APPLICATION !in curatedSlots && hasRecruitment) {
            insertSchedule(
                activityId, SLOT_APPLICATION, "신청 기간", ScheduleKind.DEADLINE,
                activity.recruitmentStartAt, activity.recruitmentEndAt, activity.recruitmentStartDate, activity.recruitmentEndDate, "", now,
            )
        }
        activity.schedules.forEach {
            insertSchedule(activityId, null, it.title, it.kind, it.startAt, it.endAt, it.startDate, it.endDate, it.dateLabel, now)
        }
        EligibilityRows.writeTerms(dsl, activityId, eligibility)
        return DraftSaveResult.SAVED
    }

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
    private fun insertSchedule(
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
    ) {
        val timed = startAt != null || endAt != null
        val allDay = !timed && (startDate != null || endDate != null)
        val start = startDate ?: endDate
        val end = endDate ?: startDate
        dsl.insertInto(SCHEDULES)
            .set(SCHEDULES.ACTIVITY_ID, activityId)
            .set(SCHEDULES.SLOT, slot)
            .set(SCHEDULES.TITLE, title)
            .set(SCHEDULES.KIND, kind.name)
            .set(SCHEDULES.STATUS, ScheduleStatus.CONFIRMED.name)
            .set(SCHEDULES.TIME_TYPE, when { timed -> TIME_TIMED; allDay -> TIME_ALL_DAY; else -> null })
            .set(SCHEDULES.START_AT, startAt)
            .set(SCHEDULES.END_AT, endAt)
            .set(SCHEDULES.TIME_ZONE, if (timed) TIME_ZONE else null)
            .set(SCHEDULES.START_DATE, if (allDay) start else null)
            .set(SCHEDULES.END_DATE_EXCLUSIVE, if (allDay) end!!.plusDays(1) else null)
            .set(SCHEDULES.DATE_LABEL, dateLabel)
            .set(SCHEDULES.SEQUENCE, 0)
            .set(SCHEDULES.CREATED_AT, now)
            .set(SCHEDULES.UPDATED_AT, now)
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
