package io.wid.dearby.domain.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Recurrence
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.TimetableRules
import io.wid.dearby.domain.ScheduleKind

import java.time.LocalDate
import java.time.OffsetDateTime

// 수집한 개별 일정. 시각이 있으면 startAt·endAt, 날짜만 있으면 startDate·endDate(종일), 둘 다 모르면 dateLabel만
data class CollectedSchedule(
    val title: String,
    val kind: ScheduleKind,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val dateLabel: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val recurrence: Recurrence? = null, // 반복 규칙. 시각 일정에만 남는다(native-v1.md "반복 일정과 겹침 결과 묶음")
    val timetable: CollectedTimetable? = null, // 하위 시간표(native-v1.md "트랙·세션 시간표"). 검증을 지난 것만
)

// 검증한 시간표(세션 태그는 아직 이름). 저장할 때 사전 이름으로 id를 찾는다
data class CollectedTimetable(val input: TimetableInput, val sessionTagNames: List<List<String>>) {
    fun withTagIds(resolve: (List<String>) -> List<Id>) = input.copy(
        sessions = input.sessions.mapIndexed { i, session -> session.copy(tagIds = resolve(sessionTagNames[i]).take(TimetableRules.MAX_TAGS)) }
    )
}
