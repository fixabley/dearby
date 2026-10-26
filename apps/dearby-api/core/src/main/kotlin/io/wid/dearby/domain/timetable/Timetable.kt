package io.wid.dearby.domain.timetable

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.Tag
import java.time.OffsetDateTime
import java.time.ZoneId

// 트랙·세션 시간표(native-v1.md "트랙·세션 시간표"): 일정 블록(Schedule) 하나에 딸린 하위 시간표.
// 트랙이 없으면(단일 진행) 세션은 모두 trackId null로 시간순 한 줄이다
data class Timetable(val scheduleId: Id, val tracks: List<Track>, val sessions: List<Session>)

// position 순서가 표시 순서. 색은 앱이 이 순서로 팔레트에서 고른다
data class Track(val id: Id, val name: String, val room: String?, val position: Int)

// trackId null = 모든 트랙에 걸친 전폭 세션(키노트·쉬는시간·점심, 또는 트랙 없는 시간표의 세션)
data class Session(
    val id: Id,
    val scheduleId: Id,
    val trackId: Id?,
    val kind: SessionKind,
    val title: String,
    val description: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val speakers: List<Speaker>,
    val tags: List<Tag>,
)

enum class SessionKind { TALK, KEYNOTE, BREAK, MEAL, OTHER }

data class Speaker(val name: String, val affiliation: String?)

// 시간표 통째 저장 입력. id가 있으면 그 트랙·세션을 고치고(세션 찜 유지), 없으면 새로 만들고, 빠진 것은 지운다.
// 세션의 track은 tracks 목록의 순번(0부터), null이면 전폭
data class TimetableInput(val tracks: List<TrackInput>, val sessions: List<SessionInput>) {
    val isEmpty: Boolean get() = tracks.isEmpty() && sessions.isEmpty()
}

data class TrackInput(val id: Id?, val name: String, val room: String?)

data class SessionInput(
    val id: Id?,
    val track: Int?,
    val kind: SessionKind,
    val title: String,
    val description: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val speakers: List<Speaker>,
    val tagIds: List<Id>,
)

object TimetableRules {
    const val MAX_TRACKS = 20
    const val MAX_SESSIONS = 300
    const val MAX_SPEAKERS = 10
    const val MAX_TAGS = 5
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    // 시간표를 붙일 수 있는 블록의 시간 창: 시작·끝이 다 있는 시각 일정, 또는 종일 일정(서울 자정~끝 날 다음 자정). 반복 일정·날짜 미정은 안 된다
    fun window(schedule: Schedule): Pair<OffsetDateTime, OffsetDateTime> = window(schedule.time, schedule.recurrence != null)

    fun window(time: ScheduleTime?, recurring: Boolean): Pair<OffsetDateTime, OffsetDateTime> {
        if (recurring) throw InvalidInputException("반복 일정에는 시간표를 붙일 수 없습니다")
        return when (time) {
            is ScheduleTime.Timed -> {
                val start = time.start ?: throw InvalidInputException("시간표를 붙이려면 일정에 시작과 끝 시각이 있어야 합니다")
                val end = time.end ?: throw InvalidInputException("시간표를 붙이려면 일정에 시작과 끝 시각이 있어야 합니다")
                start.toOffsetDateTime() to end.toOffsetDateTime()
            }
            is ScheduleTime.AllDay -> time.start.atStartOfDay(SEOUL).toOffsetDateTime() to time.endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> throw InvalidInputException("날짜가 정해지지 않은 일정에는 시간표를 붙일 수 없습니다")
        }
    }

    // 정리(앞뒤 공백)한 입력을 돌려준다. 어긋나면 400
    fun validate(input: TimetableInput, window: Pair<OffsetDateTime, OffsetDateTime>): TimetableInput {
        if (input.tracks.size > MAX_TRACKS) throw InvalidInputException("트랙은 ${MAX_TRACKS}개까지입니다")
        if (input.sessions.size > MAX_SESSIONS) throw InvalidInputException("세션은 ${MAX_SESSIONS}개까지입니다")
        val tracks = input.tracks.map { it.copy(name = it.name.trim(), room = it.room?.trim()?.ifEmpty { null }) }
        tracks.forEach {
            if (it.name.length !in 1..50) throw InvalidInputException("트랙 이름은 1~50자입니다")
            if ((it.room?.length ?: 0) > 100) throw InvalidInputException("방 이름은 100자까지입니다")
        }
        if (tracks.map { it.name }.toSet().size != tracks.size) throw InvalidInputException("같은 이름의 트랙이 있습니다")
        requireDistinctIds(tracks.mapNotNull { it.id }, "트랙")
        val (from, to) = window
        val sessions = input.sessions.map { session ->
            val cleaned = session.copy(
                title = session.title.trim(),
                description = session.description?.trim()?.ifEmpty { null },
                speakers = session.speakers.map { Speaker(it.name.trim(), it.affiliation?.trim()?.ifEmpty { null }) },
                tagIds = session.tagIds.distinct(),
            )
            if (cleaned.title.length !in 1..200) throw InvalidInputException("세션 제목은 1~200자입니다")
            if ((cleaned.description?.length ?: 0) > 2000) throw InvalidInputException("세션 설명은 2000자까지입니다")
            if (!cleaned.endAt.isAfter(cleaned.startAt)) throw InvalidInputException("세션 끝이 시작보다 뒤여야 합니다: ${cleaned.title}")
            if (cleaned.startAt.isBefore(from) || cleaned.endAt.isAfter(to)) throw InvalidInputException("세션은 일정 시간 안에 있어야 합니다: ${cleaned.title}")
            if (cleaned.track != null && cleaned.track !in tracks.indices) throw InvalidInputException("세션의 track 순번이 트랙 목록 밖입니다: ${cleaned.title}")
            if (cleaned.speakers.size > MAX_SPEAKERS) throw InvalidInputException("연사는 세션마다 ${MAX_SPEAKERS}명까지입니다")
            cleaned.speakers.forEach {
                if (it.name.length !in 1..100) throw InvalidInputException("연사 이름은 1~100자입니다")
                if ((it.affiliation?.length ?: 0) > 100) throw InvalidInputException("연사 소속은 100자까지입니다")
            }
            if (cleaned.tagIds.size > MAX_TAGS) throw InvalidInputException("세션 태그는 ${MAX_TAGS}개까지입니다")
            cleaned
        }
        requireDistinctIds(sessions.mapNotNull { it.id }, "세션")
        // 같은 트랙의 세션끼리, 전폭 세션과 다른 모든 세션은 시간이 겹치지 않는다(앱이 한 칸에 한 장씩 그린다)
        sessions.forEachIndexed { i, a ->
            sessions.drop(i + 1).forEach { b ->
                val sameLane = a.track == null || b.track == null || a.track == b.track
                if (sameLane && a.startAt.isBefore(b.endAt) && b.startAt.isBefore(a.endAt)) {
                    throw InvalidInputException("같은 트랙(또는 전폭 세션)의 시간이 겹칩니다: ${a.title} / ${b.title}")
                }
            }
        }
        return TimetableInput(tracks, sessions)
    }

    private fun requireDistinctIds(ids: List<Id>, what: String) {
        if (ids.toSet().size != ids.size) throw InvalidInputException("같은 $what id가 두 번 있습니다")
    }
}
