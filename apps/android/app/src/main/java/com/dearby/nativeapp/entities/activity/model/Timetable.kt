package com.dearby.nativeapp.entities.activity.model

import java.time.OffsetDateTime

/**
 * 트랙·세션 시간표 (contract "트랙·세션 시간표", 2026-10-26): `GET /v1/activities/:id/schedules/:scheduleId/timetable`. Tracks come in
 * [Track.position] order (the app colours them by that order); sessions start-first. A session's [Session.trackId] null spans every track
 * (키노트·쉬는시간·점심); a timetable without tracks has only such sessions. Times keep the block's own offset.
 */
data class Timetable(val activityId: String, val scheduleTitle: String, val tracks: List<Track>, val sessions: List<Session>)
data class Track(val id: String, val name: String, val room: String?, val position: Int)
data class Speaker(val name: String, val affiliation: String?)
/** [kind] is TALK, KEYNOTE, BREAK, MEAL or OTHER; [tags] are 분야 tags in the server's order. */
data class Session(
    val id: String, val scheduleId: String, val trackId: String?, val kind: String, val title: String, val description: String?,
    val startAt: OffsetDateTime, val endAt: OffsetDateTime, val speakers: List<Speaker>, val tags: List<Tag>,
) {
}

/** One `GET /v1/me/session-favorites` item (내 시간표, session start order). */
data class SessionFavorite(val activityId: String, val activityName: String, val scheduleId: String, val scheduleTitle: String, val session: Session)
