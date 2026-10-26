package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.Schedules
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.domain.ActivityCategory
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
    private const val HISTORY_MIN_DAYS = 7
    // 기간을 모를 때 단기 행사로 보는 종류. historyEligible보다 앞에 둔다(object 초기화 순서)
    private val SHORT_EVENT_CATEGORIES = setOf(ActivityCategory.CONFERENCE, ActivityCategory.WORKSHOP, ActivityCategory.MEETUP, ActivityCategory.LECTURE)

    val app: Schedules = SCHEDULES.`as`("app")
    val act: Schedules = SCHEDULES.`as`("act")
    val appJoin: Condition = app.ACTIVITY_ID.eq(ACTIVITIES.ID).and(app.SLOT.eq(SLOT_APPLICATION))
    val actJoin: Condition = act.ACTIVITY_ID.eq(ACTIVITIES.ID).and(act.SLOT.eq(SLOT_ACTIVITY))

    // 날짜 열의 서울 0시
    private fun seoulMidnight(date: Field<LocalDate?>): Field<OffsetDateTime?> =
        DSL.field("({0}::timestamp at time zone 'Asia/Seoul')", OffsetDateTime::class.java, date)

    // 활동 시작 시각: 시각 일정이면 start_at, 종일이면 start_date의 서울 0시
    val activityStart: Field<OffsetDateTime?> = DSL.coalesce(act.START_AT, seoulMidnight(act.START_DATE))
    // 활동 끝 시각: 시각 일정이면 end_at, 종일이면 end_date_exclusive의 서울 0시, 끝을 모르면 활동 시작. 활동 기간이 없으면 null
    private val activityEnd: Field<OffsetDateTime?> = DSL.coalesce(act.END_AT, seoulMidnight(act.END_DATE_EXCLUSIVE), activityStart)
    // 활동 기간이 지나지 않음(2026-10-25, 운영 NDC 2026: 모집 기간에 끝이 없어 6월 행사가 10월에도 열림). 활동 기간이 없으면 막지 않는다
    private fun notEnded(now: OffsetDateTime): Condition = activityEnd.isNull.or(activityEnd.gt(now))
    // 정렬 키(admin-v1.md "정렬 기준 보완"): 모집 마감(시각 또는 날짜) → 없으면 활동 시작 → 둘 다 없으면 null(맨 뒤)
    val deadline: Field<OffsetDateTime?> = DSL.coalesce(app.END_AT, seoulMidnight(app.END_DATE_EXCLUSIVE), activityStart)
    // 정렬 키 중 실제 모집 마감만(활동 시작으로 대체하지 않음). 발견 묶음의 `M월 d일 마감` 표기 판단용
    val recruitmentEnd: Field<OffsetDateTime?> = DSL.coalesce(app.END_AT, seoulMidnight(app.END_DATE_EXCLUSIVE))
    // 모집 시작(시각 또는 날짜) → 없으면 활동 시작. 모집 예정 활동의 "가장 이른 시작"(native-v1.md "마감 숨기기")
    val recruitmentStart: Field<OffsetDateTime?> = DSL.coalesce(app.START_AT, seoulMidnight(app.START_DATE), activityStart)

    // 게시 활동이고 (a) 시각 모집 기간이 지금을 포함, 또는 (b) 날짜 모집 기간이 서울 오늘을 포함, 또는
    // (c) 구조화된 모집 기간이 없으면(행 없음 또는 문구만) 활동 시작이 아직 오지 않음. 어느 경우든 활동 기간이 이미 끝났으면 열지 않는다
    fun open(now: OffsetDateTime): Condition {
        val today = now.atZoneSameInstant(SEOUL).toLocalDate()
        val timed = app.TIME_TYPE.eq("TIMED")
            .and(app.START_AT.isNull.or(app.START_AT.le(now)))
            .and(app.END_AT.isNull.or(app.END_AT.gt(now)))
        val allDay = app.TIME_TYPE.eq("ALL_DAY").and(app.START_DATE.le(today)).and(app.END_DATE_EXCLUSIVE.gt(today))
        val upcoming = app.ID.isNull.or(app.TIME_TYPE.isNull).and(activityStart.gt(now))
        return ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name).and(timed.or(allDay).or(upcoming)).and(notEnded(now))
    }

    // 모집 예정(native-v1.md "마감 숨기기", 2026-10-19 밤): 게시 활동이고 구조화된 모집 기간이 아직 시작되지 않음
    // (시각 모집 기간의 start_at > now, 또는 날짜 모집 기간의 start_date > 서울 오늘). 모집 기간이 없고 활동 시작이 남은 경우는 open()이 이미 연 것으로 본다
    fun upcoming(now: OffsetDateTime): Condition {
        val today = now.atZoneSameInstant(SEOUL).toLocalDate()
        val timed = app.TIME_TYPE.eq("TIMED").and(app.START_AT.gt(now))
        val allDay = app.TIME_TYPE.eq("ALL_DAY").and(app.START_DATE.gt(today))
        return ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name).and(timed.or(allDay)).and(notEnded(now))
    }

    // 활동 이력 후보(native-v1.md "활동 이력에서 단기 행사 제외", 2026-10-20): 게시 활동이고 활동 기간이 7일 이상이거나,
    // 기간을 모르면(행 없음, 문구만, 또는 시각 일정에 시작이 없음) 종류가 컨퍼런스·워크샵·밋업·강연이 아닌 것. 끝이 없는 시각 일정은 하루로 본다.
    // GET /v1/activities?forHistory=true와 이력 쓰기 검사(ProfileService)가 같은 규칙을 쓴다. null이 끼면 where에서 빠지므로 남길 조건을 양의 형태로 쓴다
    val historyEligible: Condition = run {
        val allDayLong = act.TIME_TYPE.eq("ALL_DAY")
            .and(DSL.condition("{0} - {1} >= {2}", act.END_DATE_EXCLUSIVE, act.START_DATE, DSL.inline(HISTORY_MIN_DAYS)))
        val timedLong = act.TIME_TYPE.eq("TIMED").and(act.START_AT.isNotNull).and(act.END_AT.isNotNull)
            .and(DSL.condition("{0} - {1} >= make_interval(days => {2})", act.END_AT, act.START_AT, DSL.inline(HISTORY_MIN_DAYS)))
        val unknownPeriod = act.TIME_TYPE.isNull.or(act.TIME_TYPE.eq("TIMED").and(act.START_AT.isNull))
            .and(ACTIVITIES.CATEGORY.isNull.or(ACTIVITIES.CATEGORY.notIn(SHORT_EVENT_CATEGORIES.map(ActivityCategory::name))))
        ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name).and(allDayLong.or(timedLong).or(unknownPeriod))
    }
}
