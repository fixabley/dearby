package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.domain.*
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.DraftSaveResult
import io.wid.dearby.domain.curation.Freshness
import io.wid.dearby.domain.curation.RecruitmentStatus
import org.jooq.DSLContext
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
        val existing = dsl.select(ACTIVITIES.ID, ACTIVITIES.PUBLICATION_STATUS)
            .from(ACTIVITIES)
            .where(ACTIVITIES.SOURCE_ID.eq(sourceId))
            .forUpdate()
            .fetchOne()
        if (existing?.value2() == PublicationStatus.PUBLISHED.name) return DraftSaveResult.SKIPPED_PUBLISHED
        val now = LocalDateTime.now()
        val activityId = existing?.value1() ?: dsl.insertInto(ACTIVITIES)
            .set(ACTIVITIES.SOURCE_ID, sourceId)
            .set(ACTIVITIES.PROGRAM_ID, programId)
            .set(ACTIVITIES.NAME, activity.name)
            .set(ACTIVITIES.DESCRIPTION, activity.description)
            .set(ACTIVITIES.OFFICIAL_URL, activity.officialUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, activity.participationType.name)
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
            .set(ACTIVITIES.NAME, activity.name)
            .set(ACTIVITIES.NAME_EN, activity.nameEn)
            .set(ACTIVITIES.DESCRIPTION, activity.description)
            .set(ACTIVITIES.DESCRIPTION_EN, activity.descriptionEn)
            .set(ACTIVITIES.OFFICIAL_URL, activity.officialUrl)
            .set(ACTIVITIES.OFFICIAL_SOURCE, activity.officialSource)
            .set(ACTIVITIES.APPLICATION_URL, activity.applicationUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, activity.participationType.name)
            .set(ACTIVITIES.LOCATION, activity.location)
            .set(ACTIVITIES.COST, activity.cost)
            .set(ACTIVITIES.AUDIENCE, activity.audience)
            .set(ACTIVITIES.ELIGIBILITY, EligibilityRows.jsonb(activity.eligibility))
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
            insertSchedule(activityId, SLOT_ACTIVITY, activity.name, ScheduleKind.EVENT, null, null, activity.startDate, activity.endDate, activity.dateLabel, now)
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
        EligibilityRows.writeTerms(dsl, activityId, activity.eligibility)
        return DraftSaveResult.SAVED
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
