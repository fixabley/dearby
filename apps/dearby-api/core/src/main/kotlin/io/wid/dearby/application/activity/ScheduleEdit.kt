package io.wid.dearby.application.activity

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ScheduleTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

// 운영자가 직접 지정하는 활동 기간·모집 기간(admin-v1.md "일정 직접 지정"). 날짜만 주면 종일, 시각까지 주면 시각 일정
enum class ScheduleSlot { ACTIVITY, APPLICATION }

data class ScheduleEdit(val startDate: LocalDate?, val endDate: LocalDate?, val startAt: OffsetDateTime?, val endAt: OffsetDateTime?) {

    // 날짜와 시각을 섞으면 400, 끝이 시작보다 앞서면 400. 아무 값도 없으면 null(dateLabel만 남긴다)
    fun toTime(): ScheduleTime? {
        val dated = startDate != null || endDate != null
        val timed = startAt != null || endAt != null
        if (dated && timed) throw InvalidInputException("날짜와 시각을 함께 지정할 수 없습니다")
        return when {
            dated -> {
                val start = startDate ?: endDate!!
                val end = endDate ?: startDate!!
                if (end.isBefore(start)) throw InvalidInputException("끝 날짜가 시작 날짜보다 앞섭니다")
                ScheduleTime.AllDay(start, end.plusDays(1))
            }
            timed -> {
                if (startAt != null && endAt != null && endAt.isBefore(startAt)) throw InvalidInputException("끝 시각이 시작 시각보다 앞섭니다")
                ScheduleTime.Timed(startAt?.atZoneSameInstant(SEOUL), endAt?.atZoneSameInstant(SEOUL))
            }
            else -> null
        }
    }

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
