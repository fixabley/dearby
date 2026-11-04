package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ScheduleSessionsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_ROOMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_SESSIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_SESSION_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_TRACKS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_SESSION_FAVORITES
import io.wid.dearby.application.timetable.SessionOwner
import io.wid.dearby.application.timetable.TimetableRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.timetable.Room
import io.wid.dearby.domain.timetable.Session
import io.wid.dearby.domain.timetable.SessionKind
import io.wid.dearby.domain.timetable.Speaker
import io.wid.dearby.domain.timetable.Timetable
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.Track
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime

@Repository
class JooqTimetableRepository(private val dsl: DSLContext) : TimetableRepository {

    override fun find(scheduleId: Id): Timetable = TimetableRows.find(dsl, scheduleId)

    override fun replace(scheduleId: Id, input: TimetableInput) = TimetableRows.replace(dsl, scheduleId, input)

    override fun owners(sessionIds: Collection<Id>): Map<Id, SessionOwner> =
        if (sessionIds.isEmpty()) emptyMap()
        else dsl.select(SCHEDULE_SESSIONS.ID, SCHEDULES.ACTIVITY_ID, SCHEDULES.ID).from(SCHEDULE_SESSIONS)
            .join(SCHEDULES).on(SCHEDULES.ID.eq(SCHEDULE_SESSIONS.SCHEDULE_ID))
            .where(SCHEDULE_SESSIONS.ID.`in`(sessionIds), SCHEDULES.CURATED_DELETED.isFalse)
            .fetch().associate { it.value1()!! to SessionOwner(it.value2()!!, it.value3()!!) }

    override fun sessions(ids: Collection<Id>): List<Session> =
        if (ids.isEmpty()) emptyList()
        else TimetableRows.sessions(dsl, dsl.selectFrom(SCHEDULE_SESSIONS).where(SCHEDULE_SESSIONS.ID.`in`(ids)).fetch())

    override fun tracks(ids: Collection<Id>): Map<Id, Track> =
        if (ids.isEmpty()) emptyMap()
        else TimetableRows.tracks(dsl, SCHEDULE_TRACKS.ID.`in`(ids)).associateBy { it.id }

    override fun rooms(ids: Collection<Id>): Map<Id, Room> =
        if (ids.isEmpty()) emptyMap()
        else dsl.selectFrom(SCHEDULE_ROOMS).where(SCHEDULE_ROOMS.ID.`in`(ids)).fetch { Room(it.id!!, it.name!!, it.position!!) }.associateBy { it.id }

    override fun saveFavorite(userId: UserId, sessionId: Id, at: OffsetDateTime) {
        dsl.insertInto(USER_SESSION_FAVORITES)
            .set(USER_SESSION_FAVORITES.USER_ID, userId)
            .set(USER_SESSION_FAVORITES.SESSION_ID, sessionId)
            .set(USER_SESSION_FAVORITES.CREATED_AT, at)
            .onConflictDoNothing()
            .execute()
    }

    override fun deleteFavorite(userId: UserId, sessionId: Id) {
        dsl.deleteFrom(USER_SESSION_FAVORITES)
            .where(USER_SESSION_FAVORITES.USER_ID.eq(userId), USER_SESSION_FAVORITES.SESSION_ID.eq(sessionId))
            .execute()
    }

    override fun favorites(userId: UserId): Map<Id, OffsetDateTime> =
        dsl.select(USER_SESSION_FAVORITES.SESSION_ID, USER_SESSION_FAVORITES.CREATED_AT).from(USER_SESSION_FAVORITES)
            .where(USER_SESSION_FAVORITES.USER_ID.eq(userId))
            .fetch().associate { it.value1()!! to it.value2()!! }
}

// 시간표 읽기·쓰기. 어드민 편집과 수집 저장(JooqActivityDraftRepository)이 같은 쓰기를 쓴다
internal object TimetableRows {
    private val json = JsonMapper.builder().build()

    fun find(dsl: DSLContext, scheduleId: Id): Timetable {
        val rooms = dsl.selectFrom(SCHEDULE_ROOMS).where(SCHEDULE_ROOMS.SCHEDULE_ID.eq(scheduleId))
            .orderBy(SCHEDULE_ROOMS.POSITION, SCHEDULE_ROOMS.ID)
            .fetch { Room(it.id!!, it.name!!, it.position!!) }
        val tracks = tracks(dsl, SCHEDULE_TRACKS.SCHEDULE_ID.eq(scheduleId))
        val sessions = sessions(dsl, dsl.selectFrom(SCHEDULE_SESSIONS).where(SCHEDULE_SESSIONS.SCHEDULE_ID.eq(scheduleId)).fetch())
        return Timetable(scheduleId, rooms, tracks, sessions)
    }

    // 트랙과 그 방 이름(Track.room; 옛 schedule_tracks.room은 V68 이후 읽지 않는다). position 순
    fun tracks(dsl: DSLContext, condition: Condition): List<Track> =
        dsl.select(SCHEDULE_TRACKS.ID, SCHEDULE_TRACKS.NAME, SCHEDULE_TRACKS.ROOM_ID, SCHEDULE_ROOMS.NAME, SCHEDULE_TRACKS.POSITION)
            .from(SCHEDULE_TRACKS).leftJoin(SCHEDULE_ROOMS).on(SCHEDULE_ROOMS.ID.eq(SCHEDULE_TRACKS.ROOM_ID))
            .where(condition)
            .orderBy(SCHEDULE_TRACKS.POSITION, SCHEDULE_TRACKS.ID)
            .fetch { Track(it.value1()!!, it.value2()!!, it.value3(), it.value4(), it.value5()!!) }

    // 세션이 하나라도 있는 일정(공개 Schedule.hasTimetable)
    fun scheduleIdsWithSessions(dsl: DSLContext, scheduleIds: Collection<Id>): Set<Id> =
        if (scheduleIds.isEmpty()) emptySet()
        else dsl.selectDistinct(SCHEDULE_SESSIONS.SCHEDULE_ID).from(SCHEDULE_SESSIONS)
            .where(SCHEDULE_SESSIONS.SCHEDULE_ID.`in`(scheduleIds)).fetchSet(SCHEDULE_SESSIONS.SCHEDULE_ID).filterNotNull().toSet()

    // 바깥 쿼리의 ACTIVITIES 행이 시간표 있는 일정(지우지 않은 블록)을 가졌는지(발견 ProgramSummary.hasTimetable)
    val activityHasTimetable: Condition
        get() = DSL.exists(
            DSL.selectOne().from(SCHEDULE_SESSIONS).join(SCHEDULES).on(SCHEDULES.ID.eq(SCHEDULE_SESSIONS.SCHEDULE_ID))
                .where(SCHEDULES.ACTIVITY_ID.eq(ACTIVITIES.ID), SCHEDULES.CURATED_DELETED.isFalse),
        )

    // 시작 순(같으면 id 순), 태그는 보낸 순서
    fun sessions(dsl: DSLContext, rows: List<ScheduleSessionsRecord>): List<Session> {
        if (rows.isEmpty()) return emptyList()
        val tags = dsl.select(TagRows.FIELDS + SCHEDULE_SESSION_TAGS.SESSION_ID)
            .from(SCHEDULE_SESSION_TAGS).join(TAGS).on(TAGS.ID.eq(SCHEDULE_SESSION_TAGS.TAG_ID))
            .where(SCHEDULE_SESSION_TAGS.SESSION_ID.`in`(rows.map { it.id!! }))
            .orderBy(SCHEDULE_SESSION_TAGS.POSITION)
            .fetch()
            .groupBy({ it[SCHEDULE_SESSION_TAGS.SESSION_ID]!! }) { TagRows.tag(it) }
        return rows.sortedWith(compareBy({ it.startAt }, { it.id })).map { row ->
            Session(
                id = row.id!!,
                scheduleId = row.scheduleId!!,
                trackId = row.trackId,
                roomId = row.roomId,
                kind = SessionKind.valueOf(row.kind!!),
                title = row.title!!,
                description = row.description,
                startAt = row.startAt!!,
                endAt = row.endAt!!,
                speakers = json.readValue<List<Speaker>>(row.speakers!!.data()),
                tags = tags[row.id].orEmpty(),
            )
        }
    }

    fun replace(dsl: DSLContext, scheduleId: Id, input: TimetableInput) {
        val currentRooms = dsl.select(SCHEDULE_ROOMS.ID).from(SCHEDULE_ROOMS).where(SCHEDULE_ROOMS.SCHEDULE_ID.eq(scheduleId)).fetchSet(SCHEDULE_ROOMS.ID)
        val currentTracks = dsl.select(SCHEDULE_TRACKS.ID).from(SCHEDULE_TRACKS).where(SCHEDULE_TRACKS.SCHEDULE_ID.eq(scheduleId)).fetchSet(SCHEDULE_TRACKS.ID)
        val currentSessions = dsl.select(SCHEDULE_SESSIONS.ID).from(SCHEDULE_SESSIONS).where(SCHEDULE_SESSIONS.SCHEDULE_ID.eq(scheduleId)).fetchSet(SCHEDULE_SESSIONS.ID)
        input.rooms.mapNotNull { it.id }.firstOrNull { it !in currentRooms }?.let { throw InvalidInputException("이 일정의 방이 아닙니다: $it") }
        input.tracks.mapNotNull { it.id }.firstOrNull { it !in currentTracks }?.let { throw InvalidInputException("이 일정의 트랙이 아닙니다: $it") }
        input.sessions.mapNotNull { it.id }.firstOrNull { it !in currentSessions }?.let { throw InvalidInputException("이 일정의 세션이 아닙니다: $it") }

        // 빠진 세션 → 빠진 트랙 순으로 지운다(트랙을 지우면 그 트랙 세션도 cascade로 지워지므로 남길 세션을 먼저 옮긴다)
        val keptSessions = input.sessions.mapNotNull { it.id }.toSet()
        dsl.deleteFrom(SCHEDULE_SESSIONS).where(SCHEDULE_SESSIONS.SCHEDULE_ID.eq(scheduleId), SCHEDULE_SESSIONS.ID.notIn(keptSessions)).execute()
        // 방 → 트랙 → 세션 순으로 쓴다(뒤의 것이 앞의 id를 가리킨다). 빠진 방은 마지막에 지운다(남은 참조는 on delete set null)
        val roomIds = input.rooms.mapIndexed { position, room ->
            if (room.id != null) {
                dsl.update(SCHEDULE_ROOMS).set(SCHEDULE_ROOMS.NAME, room.name).set(SCHEDULE_ROOMS.POSITION, position).where(SCHEDULE_ROOMS.ID.eq(room.id)).execute()
                room.id
            } else {
                dsl.insertInto(SCHEDULE_ROOMS).set(SCHEDULE_ROOMS.SCHEDULE_ID, scheduleId).set(SCHEDULE_ROOMS.NAME, room.name).set(SCHEDULE_ROOMS.POSITION, position)
                    .returning(SCHEDULE_ROOMS.ID).fetchOne()!!.id!!
            }
        }
        val trackIds = input.tracks.mapIndexed { position, track ->
            val roomId = track.room?.let { roomIds[it] }
            if (track.id != null) {
                dsl.update(SCHEDULE_TRACKS).set(SCHEDULE_TRACKS.NAME, track.name).set(SCHEDULE_TRACKS.ROOM_ID, roomId).set(SCHEDULE_TRACKS.POSITION, position)
                    .where(SCHEDULE_TRACKS.ID.eq(track.id)).execute()
                track.id
            } else {
                dsl.insertInto(SCHEDULE_TRACKS)
                    .set(SCHEDULE_TRACKS.SCHEDULE_ID, scheduleId).set(SCHEDULE_TRACKS.NAME, track.name).set(SCHEDULE_TRACKS.ROOM_ID, roomId).set(SCHEDULE_TRACKS.POSITION, position)
                    .returning(SCHEDULE_TRACKS.ID).fetchOne()!!.id!!
            }
        }
        input.sessions.forEach { session ->
            val trackId = session.track?.let { trackIds[it] }
            val roomId = session.room?.let { roomIds[it] }
            val speakers = JSONB.valueOf(json.writeValueAsString(session.speakers))
            val id = if (session.id != null) {
                dsl.update(SCHEDULE_SESSIONS)
                    .set(SCHEDULE_SESSIONS.TRACK_ID, trackId).set(SCHEDULE_SESSIONS.ROOM_ID, roomId).set(SCHEDULE_SESSIONS.KIND, session.kind.name).set(SCHEDULE_SESSIONS.TITLE, session.title)
                    .set(SCHEDULE_SESSIONS.DESCRIPTION, session.description).set(SCHEDULE_SESSIONS.START_AT, session.startAt).set(SCHEDULE_SESSIONS.END_AT, session.endAt)
                    .set(SCHEDULE_SESSIONS.SPEAKERS, speakers)
                    .where(SCHEDULE_SESSIONS.ID.eq(session.id)).execute()
                session.id
            } else {
                dsl.insertInto(SCHEDULE_SESSIONS)
                    .set(SCHEDULE_SESSIONS.SCHEDULE_ID, scheduleId).set(SCHEDULE_SESSIONS.TRACK_ID, trackId).set(SCHEDULE_SESSIONS.ROOM_ID, roomId).set(SCHEDULE_SESSIONS.KIND, session.kind.name)
                    .set(SCHEDULE_SESSIONS.TITLE, session.title).set(SCHEDULE_SESSIONS.DESCRIPTION, session.description)
                    .set(SCHEDULE_SESSIONS.START_AT, session.startAt).set(SCHEDULE_SESSIONS.END_AT, session.endAt).set(SCHEDULE_SESSIONS.SPEAKERS, speakers)
                    .returning(SCHEDULE_SESSIONS.ID).fetchOne()!!.id!!
            }
            dsl.deleteFrom(SCHEDULE_SESSION_TAGS).where(SCHEDULE_SESSION_TAGS.SESSION_ID.eq(id)).execute()
            session.tagIds.forEachIndexed { position, tagId ->
                dsl.insertInto(SCHEDULE_SESSION_TAGS).set(SCHEDULE_SESSION_TAGS.SESSION_ID, id).set(SCHEDULE_SESSION_TAGS.TAG_ID, tagId).set(SCHEDULE_SESSION_TAGS.POSITION, position).execute()
            }
        }
        dsl.deleteFrom(SCHEDULE_TRACKS).where(SCHEDULE_TRACKS.SCHEDULE_ID.eq(scheduleId), SCHEDULE_TRACKS.ID.notIn(trackIds)).execute()
        dsl.deleteFrom(SCHEDULE_ROOMS).where(SCHEDULE_ROOMS.SCHEDULE_ID.eq(scheduleId), SCHEDULE_ROOMS.ID.notIn(roomIds)).execute()
        // 재수집은 운영자 지정이 아닌 세부 일정을 지웠다 다시 넣는다. 시간표가 붙은 세부 일정은 운영자 지정으로 올려 id(세션·찜)를 지킨다
        if (!input.isEmpty) dsl.update(SCHEDULES).set(SCHEDULES.CURATED, true).where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.SLOT.isNull).execute()
    }
}
