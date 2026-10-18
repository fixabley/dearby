package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ROLES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.domain.*
import io.wid.dearby.domain.curation.ActivityCuration
import io.wid.dearby.domain.curation.Freshness
import io.wid.dearby.domain.curation.RecruitmentStatus
import io.wid.dearby.domain.eligibility.EligibilityGroup
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqActivityCurationRepository(private val dsl: DSLContext) : ActivityCurationRepository {

    private val json = JsonMapper.builder().findAndAddModules().build()

    override fun findAllByActivityId(activityIds: Collection<Id>): List<ActivityCuration> {
        if (activityIds.isEmpty()) return emptyList()
        val roles = dsl.selectFrom(ACTIVITY_ROLES)
            .where(ACTIVITY_ROLES.ACTIVITY_ID.`in`(activityIds))
            .orderBy(ACTIVITY_ROLES.POSITION)
            .fetch()
            .groupBy({ it.activityId!! }, { it.name!! })
        return dsl.selectFrom(ACTIVITIES).where(ACTIVITIES.ID.`in`(activityIds)).fetch().map {
            ActivityCuration(
                activityId = it.id!!,
                publicationStatus = PublicationStatus.valueOf(it.publicationStatus!!),
                officialSource = it.officialSource!!,
                participationType = ParticipationType.valueOf(it.participationType!!),
                recruitmentStatus = RecruitmentStatus.valueOf(it.recruitmentStatus!!),
                location = it.location,
                cost = it.cost,
                audience = it.audience,
                qualification = it.qualification?.data()?.let { data -> json.readValue<Map<String, List<String>>>(data) }.orEmpty(),
                roles = roles[it.id].orEmpty(),
                applicationUrl = it.applicationUrl,
                sourceCheckedAt = it.sourceCheckedAt,
                validUntil = it.validUntil,
                freshness = Freshness.valueOf(it.freshness!!),
                sourceNote = it.sourceNote!!,
                inheritedFields = InheritedFields.read(it.inheritedFields).toList(),
                curatedFields = CuratedFields.read(it.curatedFields).toList(),
            )
        }
    }

    @Transactional
    override fun delete(activityId: Id): Boolean {
        val sourceId = dsl.select(ACTIVITIES.SOURCE_ID).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()
            ?: return false
        sourceId.value1()?.let {
            dsl.insertInto(DISMISSED_ACTIVITY_SOURCES)
                .set(DISMISSED_ACTIVITY_SOURCES.SOURCE_ID, it)
                .set(DISMISSED_ACTIVITY_SOURCES.DISMISSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute()
        }
        return dsl.deleteFrom(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).execute() > 0
    }

    @Transactional
    override fun updateTexts(activityId: Id, name: String, nameEn: String?, description: String, descriptionEn: String?, touched: Set<String>): Boolean {
        // 보낸 필드는 운영자 지정 필드로 남겨 수집기가 덮어쓰지 않게 한다(admin-v1.md "운영자 지정 필드")
        val curated = dsl.select(ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()?.value1() ?: return false
        return dsl.update(ACTIVITIES)
            .set(ACTIVITIES.NAME, name)
            .set(ACTIVITIES.NAME_EN, nameEn)
            .set(ACTIVITIES.DESCRIPTION, description)
            .set(ACTIVITIES.DESCRIPTION_EN, descriptionEn)
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(curated) + touched))
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
    }

    @Transactional
    override fun updateEligibility(activityId: Id, eligibility: EligibilityGroup?): Boolean {
        // 운영자가 정한 값이므로 "이전 기수에서 가져옴" 표시를 빼고 운영자 지정 필드에 남긴다(admin-v1.md "이전 기수 정보 자동완성")
        val row = dsl.select(ACTIVITIES.INHERITED_FIELDS, ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne() ?: return false
        val updated = dsl.update(ACTIVITIES)
            .set(ACTIVITIES.ELIGIBILITY, EligibilityRows.jsonb(eligibility))
            .set(ACTIVITIES.INHERITED_FIELDS, InheritedFields.jsonb(InheritedFields.read(row.value1()) - InheritedFields.ELIGIBILITY))
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(row.value2()) + CuratedFields.ELIGIBILITY))
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
        if (updated) EligibilityRows.writeTerms(dsl, activityId, eligibility)
        return updated
    }

    @Transactional
    override fun updateSchedule(activityId: Id, slot: ScheduleSlot, time: ScheduleTime?): Boolean {
        if (!dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))) return false
        val now = LocalDateTime.now()
        val timed = time as? ScheduleTime.Timed
        val allDay = time as? ScheduleTime.AllDay
        val existing = dsl.select(SCHEDULES.ID).from(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(slot.name)).fetchOne()?.value1()
        val scheduleId = existing ?: dsl.insertInto(SCHEDULES)
            .set(SCHEDULES.ACTIVITY_ID, activityId)
            .set(SCHEDULES.SLOT, slot.name)
            .set(SCHEDULES.TITLE, if (slot == ScheduleSlot.APPLICATION) "신청 기간" else "활동 기간")
            .set(SCHEDULES.KIND, (if (slot == ScheduleSlot.APPLICATION) ScheduleKind.DEADLINE else ScheduleKind.EVENT).name)
            .set(SCHEDULES.STATUS, ScheduleStatus.CONFIRMED.name)
            .set(SCHEDULES.DATE_LABEL, "")
            .set(SCHEDULES.SEQUENCE, 0)
            .set(SCHEDULES.CREATED_AT, now)
            .set(SCHEDULES.UPDATED_AT, now)
            .returning(SCHEDULES.ID)
            .fetchOne()!!.id!!
        dsl.update(SCHEDULES)
            .set(SCHEDULES.TIME_TYPE, when { timed != null -> "TIMED"; allDay != null -> "ALL_DAY"; else -> null })
            .set(SCHEDULES.START_AT, timed?.start?.toOffsetDateTime())
            .set(SCHEDULES.END_AT, timed?.end?.toOffsetDateTime())
            .set(SCHEDULES.TIME_ZONE, timed?.let { (it.start ?: it.end)!!.zone.id })
            .set(SCHEDULES.START_DATE, allDay?.start)
            .set(SCHEDULES.END_DATE_EXCLUSIVE, allDay?.endExclusive)
            .set(SCHEDULES.CURATED, time != null)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId))
            .execute()
        dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return true
    }

    override fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.PUBLICATION_STATUS, status.name)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
}
