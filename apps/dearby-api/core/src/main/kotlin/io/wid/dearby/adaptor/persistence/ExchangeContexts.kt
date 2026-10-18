package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.card.ExchangeContext
import org.jooq.DSLContext

// 활동 id → 활동·프로그램 이름(native-v1.md "명함 교환"). 지워진 활동은 이름 없이 id만 남는다
internal fun DSLContext.exchangeContexts(activityIds: Collection<Id?>): Map<Id, ExchangeContext> {
    val ids = activityIds.filterNotNull().toSet()
    if (ids.isEmpty()) return emptyMap()
    return select(ACTIVITIES.ID, ACTIVITIES.NAME, PROGRAMS.NAME).from(ACTIVITIES).leftJoin(PROGRAMS).on(PROGRAMS.ID.eq(ACTIVITIES.PROGRAM_ID))
        .where(ACTIVITIES.ID.`in`(ids)).fetch().associate { it.value1()!! to ExchangeContext(it.value1(), it.value2(), it.value3()) }
}

internal fun Map<Id, ExchangeContext>.contextOf(activityId: Id?): ExchangeContext =
    activityId?.let { this[it] ?: ExchangeContext(it, null, null) } ?: ExchangeContext.NONE
