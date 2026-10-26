package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.adaptor.web.v1.tags.TagResponse
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.timetable.Session
import io.wid.dearby.domain.timetable.Timetable
import java.time.OffsetDateTime
import java.time.ZoneId

// GET /v1/activities/:id/schedules/:scheduleId/timetable(native-v1.md "트랙·세션 시간표")
data class TimetableResponse(val activityId: Long, val schedule: ScheduleResponse, val tracks: List<TrackResponse>, val sessions: List<SessionResponse>) {
    companion object {
        fun from(activityId: Id, schedule: ScheduleResponse, timetable: Timetable, zone: ZoneId, favorites: Set<Id>) = TimetableResponse(
            activityId,
            schedule,
            timetable.tracks.map { TrackResponse(it.id, it.name, it.room, it.position) },
            timetable.sessions.map { SessionResponse.from(it, zone, it.id in favorites) },
        )
    }
}

// position: 표시 순서(0부터). 앱이 이 순서로 팔레트에서 트랙 색을 고른다
data class TrackResponse(val id: Long, val name: String, val room: String?, val position: Int)

// trackId null = 모든 트랙에 걸친 전폭 세션(트랙이 없는 시간표는 모든 세션이 null)
data class SessionResponse(
    val id: Long,
    val scheduleId: Long,
    val trackId: Long?,
    val kind: String,
    val title: String,
    val description: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val speakers: List<SpeakerResponse>,
    val tags: List<TagResponse>,
    val favorited: Boolean,
) {
    companion object {
        // 시각은 부모 일정의 시간대 오프셋으로 쓴다(Schedule과 같다)
        fun from(session: Session, zone: ZoneId, favorited: Boolean) = SessionResponse(
            session.id, session.scheduleId, session.trackId, session.kind.name, session.title, session.description,
            session.startAt.atZoneSameInstant(zone).toOffsetDateTime(), session.endAt.atZoneSameInstant(zone).toOffsetDateTime(),
            session.speakers.map { SpeakerResponse(it.name, it.affiliation) }, session.tags.map(TagResponse::from), favorited,
        )

        fun zoneOf(time: ScheduleTime?): ZoneId = (time as? ScheduleTime.Timed)?.let { it.start ?: it.end }?.zone ?: ZoneId.of("Asia/Seoul")
    }
}

data class SpeakerResponse(val name: String, val affiliation: String?)
