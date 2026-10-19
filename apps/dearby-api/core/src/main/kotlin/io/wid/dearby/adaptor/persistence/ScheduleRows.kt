package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.SchedulesRecord
import io.wid.dearby.domain.*
import java.time.ZoneId

// schedules 행 → 도메인 Schedule. 활동 조회와 세부 일정 편집기가 같은 변환을 쓴다
internal fun SchedulesRecord.toSchedule() = Schedule(
    id = id!!,
    title = title!!,
    kind = ScheduleKind.valueOf(kind!!),
    status = ScheduleStatus.valueOf(status!!),
    time = when (timeType) {
        "TIMED" -> {
            val zone = ZoneId.of(timeZone!!)
            ScheduleTime.Timed(startAt?.atZoneSameInstant(zone), endAt?.atZoneSameInstant(zone))
        }
        "ALL_DAY" -> ScheduleTime.AllDay(startDate!!, endDateExclusive!!)
        else -> null
    },
    dateLabel = dateLabel!!,
    address = lat?.let { Address(it, lng!!, addressLine1!!, addressLine2!!) },
    conferenceUrl = conferenceUrl,
    sequence = sequence!!,
    timestamps = Timestamps(createdAt!!, updatedAt!!),
    curatedNote = curatedNote,
    curated = curated ?: false,
)
