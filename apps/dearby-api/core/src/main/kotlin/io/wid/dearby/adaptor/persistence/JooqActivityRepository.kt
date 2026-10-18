package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ActivitiesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.records.SchedulesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_CONTACTS
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
            condition.publicationStatus?.let { add(ACTIVITIES.PUBLICATION_STATUS.eq(it.name)) }
            condition.q?.takeIf { it.isNotBlank() }?.trim()?.let { q ->
                // 부분 일치 또는 단어 유사도(pg_trgm, V7). `<%`는 word_similarity(q, 열) >= 아래 기준값이면 참이고 GIN 인덱스를 탄다
                // 한글·영문 이름과 설명 모두에서 찾는다(V13)
                add(
                    DSL.or(
                        listOf(ACTIVITIES.NAME, ACTIVITIES.DESCRIPTION, ACTIVITIES.NAME_EN, ACTIVITIES.DESCRIPTION_EN).flatMap {
                            listOf(it.containsIgnoreCase(q), DSL.condition("{0} <% {1}", DSL.`val`(q), it))
                        }
                    )
                )
            }
            condition.programId?.let { add(ACTIVITIES.PROGRAM_ID.eq(it)) }
            condition.organizationId?.let {
                add(ACTIVITIES.PROGRAM_ID.`in`(DSL.select(PROGRAMS.ID).from(PROGRAMS).where(PROGRAMS.ORGANIZATION_ID.eq(it))))
            }
            condition.after?.let { add(ACTIVITIES.ID.gt(it)) }
            addAll(EligibilityRows.conditions(condition.eligibility))
        }
        // 기본값 0.6은 오타("워크샵" 0.5)·띄어쓰기 차이("개발환경" 0.4)를 놓친다. 이 트랜잭션에만 적용
        dsl.execute("select set_config('pg_trgm.word_similarity_threshold', ?, true)", WORD_SIMILARITY_THRESHOLD)
        val rows = dsl.selectFrom(ACTIVITIES)
            .where(conditions)
            .orderBy(ACTIVITIES.ID)
            .limit(condition.size)
            .fetch()
        return withDetails(rows)
    }

    override fun findById(id: Id, publicationStatus: PublicationStatus?): Activity? =
        withDetails(
            dsl.selectFrom(ACTIVITIES)
                .where(ACTIVITIES.ID.eq(id), publicationStatus?.let { ACTIVITIES.PUBLICATION_STATUS.eq(it.name) } ?: DSL.noCondition())
                .fetch()
        ).singleOrNull()

    // 일정·연락처를 한 번에 읽어 붙인다
    private fun withDetails(rows: List<ActivitiesRecord>): List<Activity> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id!! }
        val schedules = dsl.selectFrom(SCHEDULES)
            .where(SCHEDULES.ACTIVITY_ID.`in`(ids))
            .orderBy(SCHEDULES.START_AT.nullsLast(), SCHEDULES.ID)
            .fetch()
            .groupBy { it.activityId!! }
        val contacts = dsl.selectFrom(ACTIVITY_CONTACTS)
            .where(ACTIVITY_CONTACTS.ACTIVITY_ID.`in`(ids))
            .orderBy(ACTIVITY_CONTACTS.ID)
            .fetch()
            .groupBy({ it.activityId!! }) {
                ActivityContact(it.id!!, it.activityId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return rows.map { toActivity(it, schedules[it.id].orEmpty(), contacts[it.id].orEmpty()) }
    }

    private fun toActivity(
        row: ActivitiesRecord,
        schedules: List<SchedulesRecord>,
        contacts: List<ActivityContact>,
    ): Activity {
        val bySlot = schedules.groupBy { it.slot }
        val activitySchedule = bySlot[SLOT_ACTIVITY]?.single()?.let(::toSchedule)
            ?: error("Activity ${row.id} has no ACTIVITY schedule")
        return Activity(
            id = row.id!!,
            programId = row.programId!!,
            name = row.name!!,
            nameEn = row.nameEn,
            description = row.description!!,
            descriptionEn = row.descriptionEn,
            contacts = contacts,
            officialUrl = row.officialUrl!!,
            imageUrl = row.imageUrl,
            application = bySlot[SLOT_APPLICATION]?.single()?.let(::toSchedule),
            activitySchedule = activitySchedule,
            schedules = bySlot[null].orEmpty().map(::toSchedule),
            eligibility = EligibilityRows.read(row.eligibility),
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
        const val WORD_SIMILARITY_THRESHOLD = "0.4"
        const val SLOT_APPLICATION = "APPLICATION"
        const val SLOT_ACTIVITY = "ACTIVITY"
        const val TIME_TIMED = "TIMED"
        const val TIME_ALL_DAY = "ALL_DAY"
    }
}
