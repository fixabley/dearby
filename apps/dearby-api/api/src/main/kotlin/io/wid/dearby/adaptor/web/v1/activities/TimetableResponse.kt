package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.adaptor.web.v1.tags.TagResponse
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.timetable.Room
import io.wid.dearby.domain.timetable.Session
import io.wid.dearby.domain.timetable.Timetable
import io.wid.dearby.domain.timetable.Track
import java.time.OffsetDateTime
import java.time.ZoneId

// GET /v1/activities/:id/schedules/:scheduleId/timetable(native-v1.md "트랙·세션 시간표")
data class TimetableResponse(
    val activityId: Long,
    val schedule: ScheduleResponse,
    val rooms: List<RoomResponse>,
    val tracks: List<TrackResponse>,
    val sessions: List<SessionResponse>,
) {
    companion object {
        fun from(activityId: Id, schedule: ScheduleResponse, timetable: Timetable, zone: ZoneId, favorites: Set<Id>) = TimetableResponse(
            activityId,
            schedule,
            timetable.rooms.map(RoomResponse::from),
            timetable.tracks.map(TrackResponse::from),
            timetable.sessions.map { SessionResponse.from(it, zone, it.id in favorites) },
        )
    }
}

// 블록의 방(native-v1.md "같은 방이면 그 자리에서 대기"). 같은 방 판정은 id로만 한다
data class RoomResponse(val id: Long, val name: String) {
    companion object {
        fun from(room: Room) = RoomResponse(room.id, room.name)
    }
}

// position: 표시 순서(0부터). 앱이 이 순서로 팔레트에서 트랙 색을 고른다. room은 roomId 방의 이름(옛 앱 호환)
data class TrackResponse(val id: Long, val name: String, val roomId: Long?, val room: String?, val position: Int) {
    companion object {
        fun from(track: Track) = TrackResponse(track.id, track.name, track.roomId, track.room, track.position)
    }
}

// trackId null = 모든 트랙에 걸친 전폭 세션(트랙이 없는 시간표는 모든 세션이 null).
// roomId는 세션 자체의 방. null이면 트랙의 방을 따른다(앱이 session.roomId ?: track.roomId로 판정)
data class SessionResponse(
    val id: Long,
    val scheduleId: Long,
    val trackId: Long?,
    val roomId: Long?,
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
            session.id, session.scheduleId, session.trackId, session.roomId, session.kind.name, session.title, session.description,
            session.startAt.atZoneSameInstant(zone).toOffsetDateTime(), session.endAt.atZoneSameInstant(zone).toOffsetDateTime(),
            session.speakers.map { SpeakerResponse(it.name, it.affiliation) }, session.tags.map(TagResponse::from), favorited,
        )

        fun zoneOf(time: ScheduleTime?): ZoneId = (time as? ScheduleTime.Timed)?.let { it.start ?: it.end }?.zone ?: ZoneId.of("Asia/Seoul")
    }
}

data class SpeakerResponse(val name: String, val affiliation: String?)
