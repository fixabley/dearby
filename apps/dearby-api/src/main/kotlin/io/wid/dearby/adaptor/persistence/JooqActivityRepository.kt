package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ActivitiesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.records.SchedulesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_CONTACTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ROLES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.domain.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.ZoneId

@Repository
class JooqActivityRepository(private val dsl: DSLContext) : ActivityRepository {

    override fun search(condition: ActivitySearch): List<Activity> {
        val conditions = buildList<Condition> {
            condition.q?.takeIf { it.isNotBlank() }?.trim()?.let {
                add(ACTIVITIES.NAME.containsIgnoreCase(it).or(ACTIVITIES.DESCRIPTION.containsIgnoreCase(it)))
            }
            condition.programId?.let { add(ACTIVITIES.PROGRAM_ID.eq(it)) }
            condition.organizationId?.let {
                add(ACTIVITIES.PROGRAM_ID.`in`(DSL.select(PROGRAMS.ID).from(PROGRAMS).where(PROGRAMS.ORGANIZATION_ID.eq(it))))
            }
            condition.after?.let { add(ACTIVITIES.ID.gt(it)) }
        }
        val rows = dsl.selectFrom(ACTIVITIES)
            .where(conditions)
            .orderBy(ACTIVITIES.ID)
            .limit(condition.size)
            .fetch()
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id!! }
        val schedules = dsl.selectFrom(SCHEDULES)
            .where(SCHEDULES.ACTIVITY_ID.`in`(ids))
            .orderBy(SCHEDULES.START_AT.nullsLast(), SCHEDULES.ID)
            .fetch()
            .groupBy { it.activityId!! }
        val roles = dsl.selectFrom(ACTIVITY_ROLES)
            .where(ACTIVITY_ROLES.ACTIVITY_ID.`in`(ids))
            .orderBy(ACTIVITY_ROLES.POSITION)
            .fetch()
            .groupBy({ it.activityId!! }, { it.name!! })
        val contacts = dsl.selectFrom(ACTIVITY_CONTACTS)
            .where(ACTIVITY_CONTACTS.ACTIVITY_ID.`in`(ids))
            .orderBy(ACTIVITY_CONTACTS.ID)
            .fetch()
            .groupBy({ it.activityId!! }) {
                ActivityContact(it.id!!, it.activityId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return rows.map { toActivity(it, schedules[it.id].orEmpty(), roles[it.id].orEmpty(), contacts[it.id].orEmpty()) }
    }

    private fun toActivity(
        row: ActivitiesRecord,
        schedules: List<SchedulesRecord>,
        roles: List<String>,
        contacts: List<ActivityContact>,
    ): Activity {
        val bySlot = schedules.groupBy { it.slot }
        val activitySchedule = bySlot[SLOT_ACTIVITY]?.single()?.let(::toSchedule)
            ?: error("Activity ${row.id} has no ACTIVITY schedule")
        return Activity(
            id = row.id!!,
            programId = row.programId!!,
            name = row.name!!,
            description = row.description!!,
            contacts = contacts,
            participationType = ParticipationType.valueOf(row.participationType!!),
            recruitmentStatus = RecruitmentStatus.valueOf(row.recruitmentStatus!!),
            location = row.location,
            cost = row.cost,
            audience = row.audience,
            roles = roles,
            officialUrl = row.officialUrl!!,
            applicationUrl = row.applicationUrl,
            sourceCheckedAt = row.sourceCheckedAt,
            validUntil = row.validUntil,
            freshness = Freshness.valueOf(row.freshness!!),
            sourceNote = row.sourceNote!!,
            application = bySlot[SLOT_APPLICATION]?.single()?.let(::toSchedule),
            activitySchedule = activitySchedule,
            schedules = bySlot[null].orEmpty().map(::toSchedule),
            timestamps = Timestamps(row.createdAt!!, row.updatedAt!!),
        )
    }

    private fun toSchedule(row: SchedulesRecord) = Schedule(
        id = row.id!!,
        title = row.title!!,
        kind = ScheduleKind.valueOf(row.kind!!),
        status = ScheduleStatus.valueOf(row.status!!),
        time = when (row.timeType) {
            TIME_TIMED -> {
                val zone = ZoneId.of(row.timeZone!!)
                ScheduleTime.Timed(row.startAt?.atZoneSameInstant(zone), row.endAt?.atZoneSameInstant(zone))
            }
            TIME_ALL_DAY -> ScheduleTime.AllDay(row.startDate!!, row.endDateExclusive!!)
            else -> null
        },
        dateLabel = row.dateLabel!!,
        address = row.lat?.let { Address(it, row.lng!!, row.addressLine1!!, row.addressLine2!!) },
        conferenceUrl = row.conferenceUrl,
        sequence = row.sequence!!,
        timestamps = Timestamps(row.createdAt!!, row.updatedAt!!),
    )

    private companion object {
        const val SLOT_APPLICATION = "APPLICATION"
        const val SLOT_ACTIVITY = "ACTIVITY"
        const val TIME_TIMED = "TIMED"
        const val TIME_ALL_DAY = "ALL_DAY"
    }
}
