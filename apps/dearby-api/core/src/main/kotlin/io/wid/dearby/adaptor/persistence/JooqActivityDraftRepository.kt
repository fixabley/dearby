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
            .set(ACTIVITIES.SOURCE_CHECKED_AT, activity.checkedAt)
            .set(ACTIVITIES.VALID_UNTIL, activity.checkedAt.plus(VERIFICATION_LIFETIME))
            .set(ACTIVITIES.FRESHNESS, Freshness.VERIFIED.name)
            .set(ACTIVITIES.UPDATED_AT, now)
            .where(ACTIVITIES.ID.eq(activityId))
            .execute()
        // 일정은 매번 수집 결과로 바꾼다(초안이라 관리자가 고친 내용이 없다)
        dsl.deleteFrom(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId)).execute()
        insertSchedule(activityId, SLOT_ACTIVITY, activity.name, ScheduleKind.EVENT, null, null, activity.dateLabel, now)
        if (activity.recruitmentStartAt != null || activity.recruitmentEndAt != null) {
            insertSchedule(
                activityId, SLOT_APPLICATION, "신청 기간", ScheduleKind.DEADLINE,
                activity.recruitmentStartAt, activity.recruitmentEndAt, "", now,
            )
        }
        activity.schedules.forEach {
            insertSchedule(activityId, null, it.title, ScheduleKind.EVENT, it.startAt, it.endAt, it.dateLabel, now)
        }
        return DraftSaveResult.SAVED
    }

    private fun insertSchedule(
        activityId: Id,
        slot: String?,
        title: String,
        kind: ScheduleKind,
        startAt: OffsetDateTime?,
        endAt: OffsetDateTime?,
        dateLabel: String,
        now: LocalDateTime,
    ) {
        val timed = startAt != null || endAt != null
        dsl.insertInto(SCHEDULES)
            .set(SCHEDULES.ACTIVITY_ID, activityId)
            .set(SCHEDULES.SLOT, slot)
            .set(SCHEDULES.TITLE, title)
            .set(SCHEDULES.KIND, kind.name)
            .set(SCHEDULES.STATUS, ScheduleStatus.CONFIRMED.name)
            .set(SCHEDULES.TIME_TYPE, if (timed) TIME_TIMED else null)
            .set(SCHEDULES.START_AT, startAt)
            .set(SCHEDULES.END_AT, endAt)
            .set(SCHEDULES.TIME_ZONE, if (timed) TIME_ZONE else null)
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
        const val TIME_ZONE = "Asia/Seoul" // 한국 행사만 수집한다
        val VERIFICATION_LIFETIME: Duration = Duration.ofHours(24) // 확인 내용이 유효한 기간(기존 수집기와 같음)
    }
}
