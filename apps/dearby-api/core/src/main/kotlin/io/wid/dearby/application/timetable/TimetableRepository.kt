package io.wid.dearby.application.timetable

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.timetable.Session
import io.wid.dearby.domain.timetable.Timetable
import io.wid.dearby.domain.timetable.TimetableInput
import java.time.OffsetDateTime

// 저장소 포트(native-v1.md "트랙·세션 시간표"). 구현은 adaptor/persistence. 세션 찜은 users cascade라 계정 삭제와 함께 지워진다
interface TimetableRepository {

    fun find(scheduleId: Id): Timetable // 없으면 빈 트랙·세션

    // 검증한 입력으로 통째로 바꾼다: id가 있는 트랙·세션은 고치고(찜 유지), 없는 것은 만들고, 빠진 것은 지운다.
    // 다른 일정의 id면 400. 세부 일정 블록에 시간표가 생기면 그 블록을 운영자 지정(curated)으로 올린다(재수집이 지우지 않게)
    fun replace(scheduleId: Id, input: TimetableInput)

    fun sessionOwner(sessionId: Id): SessionOwner? = owners(listOf(sessionId))[sessionId]
    fun owners(sessionIds: Collection<Id>): Map<Id, SessionOwner> // 지운 세부 일정(curated_deleted)의 세션은 빠진다
    fun sessions(ids: Collection<Id>): List<Session>

    fun saveFavorite(userId: UserId, sessionId: Id, at: OffsetDateTime) // 이미 있으면 그대로(시각도)
    fun deleteFavorite(userId: UserId, sessionId: Id)
    fun favorites(userId: UserId): Map<Id, OffsetDateTime> // 세션 id → 찜한 시각
}

data class SessionOwner(val activityId: Id, val scheduleId: Id)
