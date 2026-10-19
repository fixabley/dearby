package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.Schedules
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.domain.PublicationStatus
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

// 열린 활동 판정(native-v1.md "열린 활동 판정 일원화", 2026-10-19). 발견 묶음과 GET /v1/activities?open=true가 같은 규칙을 쓴다.
// ACTIVITIES에 모집 기간(app)·활동 기간(act) 일정을 활동마다 하나씩 left join 한 뒤 쓴다(unique (activity_id, slot))
object OpenActivityConditions {
    private const val SLOT_APPLICATION = "APPLICATION"
    private const val SLOT_ACTIVITY = "ACTIVITY"
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    val app: Schedules = SCHEDULES.`as`("app")
    val act: Schedules = SCHEDULES.`as`("act")
    val appJoin: Condition = app.ACTIVITY_ID.eq(ACTIVITIES.ID).and(app.SLOT.eq(SLOT_APPLICATION))
    val actJoin: Condition = act.ACTIVITY_ID.eq(ACTIVITIES.ID).and(act.SLOT.eq(SLOT_ACTIVITY))

    // 날짜 열의 서울 0시
    private fun seoulMidnight(date: Field<LocalDate?>): Field<OffsetDateTime?> =
        DSL.field("({0}::timestamp at time zone 'Asia/Seoul')", OffsetDateTime::class.java, date)

    // 활동 시작 시각: 시각 일정이면 start_at, 종일이면 start_date의 서울 0시
    val activityStart: Field<OffsetDateTime?> = DSL.coalesce(act.START_AT, seoulMidnight(act.START_DATE))
    // 정렬 키(admin-v1.md "정렬 기준 보완"): 모집 마감(시각 또는 날짜) → 없으면 활동 시작 → 둘 다 없으면 null(맨 뒤)
    val deadline: Field<OffsetDateTime?> = DSL.coalesce(app.END_AT, seoulMidnight(app.END_DATE_EXCLUSIVE), activityStart)
    // 모집 시작(시각 또는 날짜) → 없으면 활동 시작. 모집 예정 활동의 "가장 이른 시작"(native-v1.md "마감 숨기기")
    val recruitmentStart: Field<OffsetDateTime?> = DSL.coalesce(app.START_AT, seoulMidnight(app.START_DATE), activityStart)

    // 게시 활동이고 (a) 시각 모집 기간이 지금을 포함, 또는 (b) 날짜 모집 기간이 서울 오늘을 포함, 또는
    // (c) 구조화된 모집 기간이 없으면(행 없음 또는 문구만) 활동 시작이 아직 오지 않음
    fun open(now: OffsetDateTime): Condition {
        val today = now.atZoneSameInstant(SEOUL).toLocalDate()
        val timed = app.TIME_TYPE.eq("TIMED")
            .and(app.START_AT.isNull.or(app.START_AT.le(now)))
            .and(app.END_AT.isNull.or(app.END_AT.gt(now)))
        val allDay = app.TIME_TYPE.eq("ALL_DAY").and(app.START_DATE.le(today)).and(app.END_DATE_EXCLUSIVE.gt(today))
        val upcoming = app.ID.isNull.or(app.TIME_TYPE.isNull).and(activityStart.gt(now))
        return ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name).and(timed.or(allDay).or(upcoming))
    }

    // 모집 예정(native-v1.md "마감 숨기기", 2026-10-19 밤): 게시 활동이고 구조화된 모집 기간이 아직 시작되지 않음
    // (시각 모집 기간의 start_at > now, 또는 날짜 모집 기간의 start_date > 서울 오늘). 모집 기간이 없고 활동 시작이 남은 경우는 open()이 이미 연 것으로 본다
    fun upcoming(now: OffsetDateTime): Condition {
        val today = now.atZoneSameInstant(SEOUL).toLocalDate()
        val timed = app.TIME_TYPE.eq("TIMED").and(app.START_AT.gt(now))
        val allDay = app.TIME_TYPE.eq("ALL_DAY").and(app.START_DATE.gt(today))
        return ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name).and(timed.or(allDay))
    }
}
