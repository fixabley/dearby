package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_SESSIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_TRACKS
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.ExchangeRef
import org.jooq.DSLContext

// 저장된 교환 id → 활동·프로그램·블록·세션·트랙 이름(native-v1.md "명함 교환" → "교환 세션 맥락"). 지워진 것은 id만 남고 이름은 null
internal class ExchangeContexts(
    private val activities: Map<Id, Pair<String, String?>>, // 활동 이름, 프로그램 이름
    private val schedules: Map<Id, String>, // 블록 제목(지운 블록 제외)
    private val sessions: Map<Id, Triple<String, String?, String?>>, // 세션 제목, 트랙 이름, 방
) {
    fun of(ref: ExchangeRef): ExchangeContext {
        val activity = ref.activityId?.let(activities::get)
        val session = ref.sessionId?.let(sessions::get)
        return ExchangeContext(ref, activity?.first, activity?.second, ref.scheduleId?.let(schedules::get), session?.first, session?.second, session?.third)
    }
}

internal fun DSLContext.exchangeContexts(refs: Collection<ExchangeRef>): ExchangeContexts {
    val activityIds = refs.mapNotNull { it.activityId }.toSet()
    val scheduleIds = refs.mapNotNull { it.scheduleId }.toSet()
    val sessionIds = refs.mapNotNull { it.sessionId }.toSet()
    val activities = if (activityIds.isEmpty()) emptyMap() else
        select(ACTIVITIES.ID, ACTIVITIES.NAME, PROGRAMS.NAME).from(ACTIVITIES).leftJoin(PROGRAMS).on(PROGRAMS.ID.eq(ACTIVITIES.PROGRAM_ID))
            .where(ACTIVITIES.ID.`in`(activityIds)).fetch().associate { it.value1()!! to (it.value2()!! to it.value3()) }
    val schedules = if (scheduleIds.isEmpty()) emptyMap() else
        select(SCHEDULES.ID, SCHEDULES.TITLE).from(SCHEDULES).where(SCHEDULES.ID.`in`(scheduleIds), SCHEDULES.CURATED_DELETED.isFalse)
            .fetch().associate { it.value1()!! to it.value2()!! }
    val sessions = if (sessionIds.isEmpty()) emptyMap() else
        select(SCHEDULE_SESSIONS.ID, SCHEDULE_SESSIONS.TITLE, SCHEDULE_TRACKS.NAME, SCHEDULE_TRACKS.ROOM).from(SCHEDULE_SESSIONS)
            .leftJoin(SCHEDULE_TRACKS).on(SCHEDULE_TRACKS.ID.eq(SCHEDULE_SESSIONS.TRACK_ID))
            .where(SCHEDULE_SESSIONS.ID.`in`(sessionIds)).fetch().associate { it.value1()!! to Triple(it.value2()!!, it.value3(), it.value4()) }
    return ExchangeContexts(activities, schedules, sessions)
}
