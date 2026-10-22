package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ROLES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.activity.ActivityCreation
import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ScheduleItem
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.application.activity.StoredSchedule
import io.wid.dearby.domain.*
import io.wid.dearby.domain.curation.ActivityCuration
import io.wid.dearby.domain.curation.Freshness
import io.wid.dearby.domain.curation.RecruitmentStatus
import io.wid.dearby.domain.eligibility.EligibilityGroup
import org.jooq.DSLContext
import org.jooq.impl.DSL
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
    override fun updateTexts(
        activityId: Id, name: String, nameEn: String?, description: String, descriptionEn: String?,
        officialUrl: String, applicationUrl: String?, touched: Set<String>,
    ): Boolean {
        // 보낸 필드는 운영자 지정 필드로 남겨 수집기가 덮어쓰지 않게 한다(admin-v1.md "운영자 지정 필드")
        val curated = dsl.select(ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()?.value1() ?: return false
        return dsl.update(ACTIVITIES)
            .set(ACTIVITIES.NAME, name)
            .set(ACTIVITIES.NAME_EN, nameEn)
            .set(ACTIVITIES.DESCRIPTION, description)
            .set(ACTIVITIES.DESCRIPTION_EN, descriptionEn)
            .set(ACTIVITIES.OFFICIAL_URL, officialUrl)
            .set(ACTIVITIES.APPLICATION_URL, applicationUrl)
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(curated) + touched))
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
    }

    @Transactional
    override fun updateEligibility(activityId: Id, eligibility: EligibilityGroup?, curate: Boolean): Boolean {
        // 운영자가 정한 값이므로 "이전 기수에서 가져옴" 표시를 빼고 운영자 지정 필드에 남긴다(admin-v1.md "이전 기수 정보 자동완성")
        val row = dsl.select(ACTIVITIES.INHERITED_FIELDS, ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne() ?: return false
        val updated = dsl.update(ACTIVITIES)
            .set(ACTIVITIES.ELIGIBILITY, EligibilityRows.jsonb(eligibility))
            .set(ACTIVITIES.INHERITED_FIELDS, InheritedFields.jsonb(InheritedFields.read(row.value1()) - InheritedFields.ELIGIBILITY))
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(row.value2()) + listOfNotNull(CuratedFields.ELIGIBILITY.takeIf { curate })))
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
        if (updated) EligibilityRows.writeTerms(dsl, activityId, eligibility)
        return updated
    }

    @Transactional
    override fun insert(creation: ActivityCreation): Id {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(ACTIVITIES)
            .set(ACTIVITIES.PROGRAM_ID, creation.programId)
            .set(ACTIVITIES.NAME, creation.name)
            .set(ACTIVITIES.NAME_EN, creation.nameEn)
            .set(ACTIVITIES.DESCRIPTION, creation.description)
            .set(ACTIVITIES.DESCRIPTION_EN, creation.descriptionEn)
            .set(ACTIVITIES.OFFICIAL_URL, creation.officialUrl)
            .set(ACTIVITIES.OFFICIAL_SOURCE, true)
            .set(ACTIVITIES.APPLICATION_URL, creation.applicationUrl)
            .set(ACTIVITIES.PARTICIPATION_TYPE, creation.participationType.name)
            .set(ACTIVITIES.CATEGORY, creation.category?.name)
            .set(ACTIVITIES.RECRUITMENT_STATUS, RecruitmentStatus.UNKNOWN.name)
            .set(ACTIVITIES.AUDIENCE, creation.audience)
            .set(ACTIVITIES.LOCATION, creation.location)
            .set(ACTIVITIES.COST, creation.cost)
            .set(ACTIVITIES.SOURCE_CHECKED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(ACTIVITIES.FRESHNESS, Freshness.VERIFIED.name)
            .set(ACTIVITIES.SOURCE_NOTE, "운영자 등록")
            .set(ACTIVITIES.PUBLICATION_STATUS, PublicationStatus.DRAFT.name)
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(creation.curatedFields))
            .set(ACTIVITIES.CREATED_AT, now)
            .set(ACTIVITIES.UPDATED_AT, now)
            .returning(ACTIVITIES.ID)
            .fetchOne()!!.id!!
        // 활동 기간 자리는 항상 있어야 한다(공개·어드민 응답의 activitySchedule). 날짜 범위는 updateSchedule이 이어서 넣는다
        dsl.insertInto(SCHEDULES)
            .set(SCHEDULES.ACTIVITY_ID, id)
            .set(SCHEDULES.SLOT, ScheduleSlot.ACTIVITY.name)
            .set(SCHEDULES.TITLE, "활동 기간")
            .set(SCHEDULES.KIND, ScheduleKind.EVENT.name)
            .set(SCHEDULES.STATUS, ScheduleStatus.CONFIRMED.name)
            .set(SCHEDULES.DATE_LABEL, creation.dateLabel)
            .set(SCHEDULES.CURATED, true)
            .set(SCHEDULES.SEQUENCE, 0)
            .set(SCHEDULES.CREATED_AT, now)
            .set(SCHEDULES.UPDATED_AT, now)
            .execute()
        return id
    }

    override fun updateCategory(activityId: Id, category: ActivityCategory?): Boolean {
        val curated = dsl.select(ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()?.value1() ?: return false
        return dsl.update(ACTIVITIES)
            .set(ACTIVITIES.CATEGORY, category?.name)
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(curated) + CuratedFields.CATEGORY))
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
    }

    override fun applyProgramCategory(programId: Id, category: ActivityCategory): Int =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.CATEGORY, category.name)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(
                ACTIVITIES.PROGRAM_ID.eq(programId),
                ACTIVITIES.CATEGORY.isNull.or(DSL.condition("not jsonb_exists({0}, 'category')", ACTIVITIES.CURATED_FIELDS)),
            )
            .execute()

    override fun existsName(programId: Id, name: String): Boolean =
        dsl.fetchExists(ACTIVITIES, ACTIVITIES.PROGRAM_ID.eq(programId), withoutSpaces(ACTIVITIES.NAME).eq(withoutSpaces(DSL.`val`(name))))

    @Transactional
    override fun updateSchedule(activityId: Id, slot: ScheduleSlot, time: ScheduleTime?, note: String?): Boolean {
        if (!dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))) return false
        val now = LocalDateTime.now()
        val timed = time as? ScheduleTime.Timed
        val allDay = time as? ScheduleTime.AllDay
        val scheduleId = slotScheduleId(activityId, slot, now)
        dsl.update(SCHEDULES)
            .set(SCHEDULES.TIME_TYPE, when { timed != null -> "TIMED"; allDay != null -> "ALL_DAY"; else -> null })
            .set(SCHEDULES.START_AT, timed?.start?.toOffsetDateTime())
            .set(SCHEDULES.END_AT, timed?.end?.toOffsetDateTime())
            .set(SCHEDULES.TIME_ZONE, timed?.let { (it.start ?: it.end)!!.zone.id })
            .set(SCHEDULES.START_DATE, allDay?.start)
            .set(SCHEDULES.END_DATE_EXCLUSIVE, allDay?.endExclusive)
            .set(SCHEDULES.CURATED, time != null)
            .set(SCHEDULES.CURATED_NOTE, if (time != null) note?.trim()?.ifEmpty { null } else null)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId))
            .execute()
        dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return true
    }

    // 활동 장소(native-v1.md "활동 장소"): 활동 기간 행의 네 열을 함께 쓴다(CHECK: 모두 null이거나 모두 값). curated(시간 지정) 플래그는 건드리지 않고
    // activities.curated_fields에 place를 남겨 수집기가 다시 쓸 때 보존한다
    @Transactional
    override fun updatePlace(activityId: Id, place: Address?, curate: Boolean): Boolean {
        val curated = dsl.select(ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()?.value1() ?: return false
        val now = LocalDateTime.now()
        val scheduleId = slotScheduleId(activityId, ScheduleSlot.ACTIVITY, now)
        dsl.update(SCHEDULES)
            .set(SCHEDULES.ADDRESS_LINE1, place?.addressLine1)
            .set(SCHEDULES.ADDRESS_LINE2, place?.addressLine2)
            .set(SCHEDULES.LAT, place?.lat)
            .set(SCHEDULES.LNG, place?.lng)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId))
            .execute()
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(curated) + listOfNotNull(CuratedFields.PLACE.takeIf { curate })))
            .set(ACTIVITIES.UPDATED_AT, now)
            .where(ACTIVITIES.ID.eq(activityId))
            .execute()
        return true
    }

    // 슬롯 일정(모집·활동 기간) 행 id. 없으면 날짜 없는 행을 만든다
    private fun slotScheduleId(activityId: Id, slot: ScheduleSlot, now: LocalDateTime): Id =
        dsl.select(SCHEDULES.ID).from(SCHEDULES).where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.eq(slot.name)).fetchOne()?.value1()
            ?: dsl.insertInto(SCHEDULES)
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

    @Transactional
    override fun insertDetailSchedule(activityId: Id, item: ScheduleItem): Schedule? {
        if (!dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))) return null
        val now = LocalDateTime.now()
        val id = dsl.insertInto(SCHEDULES)
            .set(SCHEDULES.ACTIVITY_ID, activityId)
            .set(SCHEDULES.SLOT, null as String?)
            .set(SCHEDULES.TITLE, item.title)
            .set(SCHEDULES.KIND, item.kind.name)
            .set(SCHEDULES.STATUS, item.status.name)
            .set(SCHEDULES.DATE_LABEL, item.dateLabel)
            .set(SCHEDULES.CURATED, true)
            .set(SCHEDULES.CURATED_NOTE, item.note)
            .set(SCHEDULES.OVERLAP_START_TIME, item.overlapCheck?.startTime)
            .set(SCHEDULES.OVERLAP_END_TIME, item.overlapCheck?.endTime)
            .set(SCHEDULES.RECURRENCE, item.recurrence?.rule)
            .set(SCHEDULES.SEQUENCE, 0)
            .set(SCHEDULES.CREATED_AT, now)
            .set(SCHEDULES.UPDATED_AT, now)
            .returning(SCHEDULES.ID)
            .fetchOne()!!.id!!
        writeTime(id, item.time, now)
        dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return fetchSchedule(activityId, id)
    }

    override fun findSchedule(activityId: Id, scheduleId: Id): StoredSchedule? =
        dsl.selectFrom(SCHEDULES)
            .where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED_DELETED.isFalse)
            .fetchOne()?.let { StoredSchedule(it.toSchedule(progressStatusesOf(dsl, listOf(scheduleId))[scheduleId].orEmpty()), it.slot) }

    @Transactional
    override fun updateDetailSchedule(activityId: Id, scheduleId: Id, item: ScheduleItem): Schedule? {
        val now = LocalDateTime.now()
        val updated = dsl.update(SCHEDULES)
            .set(SCHEDULES.TITLE, item.title)
            .set(SCHEDULES.KIND, item.kind.name)
            .set(SCHEDULES.STATUS, item.status.name)
            .set(SCHEDULES.DATE_LABEL, item.dateLabel)
            .set(SCHEDULES.CURATED, true)
            .set(SCHEDULES.CURATED_NOTE, item.note)
            .set(SCHEDULES.OVERLAP_START_TIME, item.overlapCheck?.startTime)
            .set(SCHEDULES.OVERLAP_END_TIME, item.overlapCheck?.endTime)
            .set(SCHEDULES.RECURRENCE, item.recurrence?.rule)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.isNull, SCHEDULES.CURATED_DELETED.isFalse)
            .execute()
        if (updated == 0) return null
        writeTime(scheduleId, item.time, now)
        dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return fetchSchedule(activityId, scheduleId)
    }

    @Transactional
    override fun deleteDetailSchedule(activityId: Id, scheduleId: Id): Boolean {
        val now = LocalDateTime.now()
        val marked = dsl.update(SCHEDULES)
            .set(SCHEDULES.CURATED_DELETED, true)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.SLOT.isNull, SCHEDULES.CURATED_DELETED.isFalse)
            .execute() > 0
        if (marked) dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return marked
    }

    @Transactional
    override fun updateTiming(activityId: Id, scheduleId: Id, overlapCheck: OverlapCheck?, recurrence: Recurrence?): Schedule? {
        val now = LocalDateTime.now()
        val updated = dsl.update(SCHEDULES)
            .set(SCHEDULES.OVERLAP_START_TIME, overlapCheck?.startTime)
            .set(SCHEDULES.OVERLAP_END_TIME, overlapCheck?.endTime)
            .set(SCHEDULES.RECURRENCE, recurrence?.rule)
            .set(SCHEDULES.SEQUENCE, SCHEDULES.SEQUENCE.plus(1))
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED_DELETED.isFalse)
            .execute()
        if (updated == 0) return null
        // 수집한 세부 일정은 다시 수집할 때 지워지므로 운영자 지정으로 남긴다(모집·활동 기간 행은 제자리에서 고쳐져 id가 유지된다)
        dsl.update(SCHEDULES).set(SCHEDULES.CURATED, true).where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.SLOT.isNull).execute()
        dsl.update(ACTIVITIES).set(ACTIVITIES.UPDATED_AT, now).where(ACTIVITIES.ID.eq(activityId)).execute()
        return fetchSchedule(activityId, scheduleId)
    }

    // 시각 일정은 start_at·end_at·time_zone, 종일 일정은 start_date·end_date_exclusive, 없으면 모두 null(dateLabel만)
    private fun writeTime(scheduleId: Id, time: ScheduleTime?, now: LocalDateTime) {
        val timed = time as? ScheduleTime.Timed
        val allDay = time as? ScheduleTime.AllDay
        dsl.update(SCHEDULES)
            .set(SCHEDULES.TIME_TYPE, when { timed != null -> "TIMED"; allDay != null -> "ALL_DAY"; else -> null })
            .set(SCHEDULES.START_AT, timed?.start?.toOffsetDateTime())
            .set(SCHEDULES.END_AT, timed?.end?.toOffsetDateTime())
            .set(SCHEDULES.TIME_ZONE, timed?.let { (it.start ?: it.end)!!.zone.id })
            .set(SCHEDULES.START_DATE, allDay?.start)
            .set(SCHEDULES.END_DATE_EXCLUSIVE, allDay?.endExclusive)
            .set(SCHEDULES.UPDATED_AT, now)
            .where(SCHEDULES.ID.eq(scheduleId))
            .execute()
    }

    private fun fetchSchedule(activityId: Id, scheduleId: Id): Schedule? =
        dsl.selectFrom(SCHEDULES).where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.ACTIVITY_ID.eq(activityId)).fetchOne()
            ?.toSchedule(progressStatusesOf(dsl, listOf(scheduleId))[scheduleId].orEmpty())

    override fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.PUBLICATION_STATUS, status.name)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
}
