package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.timetable.SessionInput
import io.wid.dearby.domain.timetable.SessionKind
import io.wid.dearby.domain.timetable.Speaker
import io.wid.dearby.domain.timetable.Timetable
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.TrackInput
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// PUT /v1/admin/activities/:id/schedules/:scheduleId/timetable(admin-v1.md "트랙·세션 시간표"). 통째로 바꾼다:
// id가 있으면 고치고(세션 찜 유지), 없으면 새로, 빠지면 지운다. 세션의 track은 tracks 순번(0부터), null이면 전폭. 빈 목록 둘이면 시간표를 지운다
data class TimetableRequest(val tracks: List<TrackItem> = emptyList(), val sessions: List<SessionItem> = emptyList()) {
    fun toInput() = TimetableInput(tracks.map { TrackInput(it.id, it.name.orEmpty(), it.room) }, sessions.map { it.toInput() })
}

data class TrackItem(val id: Long? = null, val name: String? = null, val room: String? = null)

data class SpeakerItem(val name: String? = null, val affiliation: String? = null)

data class SessionItem(
    val id: Long? = null,
    val track: Int? = null,
    val kind: String? = null,
    val title: String? = null,
    val description: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val speakers: List<SpeakerItem> = emptyList(),
    val tagIds: List<Long> = emptyList(),
) {
    fun toInput() = SessionInput(
        id = id,
        track = track,
        kind = SessionKind.entries.firstOrNull { it.name.equals(kind?.trim(), ignoreCase = true) }
            ?: throw InvalidInputException("세션 kind는 TALK·KEYNOTE·BREAK·MEAL·OTHER 중 하나입니다"),
        title = title.orEmpty(),
        description = description,
        startAt = time(startAt, "startAt"),
        endAt = time(endAt, "endAt"),
        speakers = speakers.map { Speaker(it.name.orEmpty(), it.affiliation) },
        tagIds = tagIds,
    )

    private fun time(value: String?, field: String): OffsetDateTime = try {
        OffsetDateTime.parse(value ?: throw InvalidInputException("세션 ${field}가 필요합니다"))
    } catch (e: DateTimeParseException) {
        throw InvalidInputException("세션 ${field}는 오프셋이 있는 ISO 8601 시각입니다")
    }
}

// 응답: 공개 시간표와 같은 모양에서 favorited 대신 tagIds·트랙 순번을 함께 준다(편집 폼이 그대로 다시 보낼 수 있게)
data class AdminTimetableResponse(val scheduleId: Long, val tracks: List<AdminTrackResponse>, val sessions: List<AdminSessionResponse>) {
    companion object {
        fun from(timetable: Timetable): AdminTimetableResponse {
            val index = timetable.tracks.mapIndexed { i, track -> track.id to i }.toMap()
            return AdminTimetableResponse(
                timetable.scheduleId,
                timetable.tracks.map { AdminTrackResponse(it.id, it.name, it.room, it.position) },
                timetable.sessions.map {
                    AdminSessionResponse(
                        it.id, it.trackId, it.trackId?.let(index::get), it.kind.name, it.title, it.description, it.startAt, it.endAt,
                        it.speakers.map { s -> SpeakerItem(s.name, s.affiliation) }, it.tags.map(TagResponse::from),
                    )
                },
            )
        }
    }
}

data class AdminTrackResponse(val id: Long, val name: String, val room: String?, val position: Int)

data class AdminSessionResponse(
    val id: Long,
    val trackId: Long?,
    val track: Int?,
    val kind: String,
    val title: String,
    val description: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val speakers: List<SpeakerItem>,
    val tags: List<TagResponse>,
)
