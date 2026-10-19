package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Tag
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

// 태그 연결 읽기(native-v1.md "태그"). 활동·프로그램 저장소가 목록을 채울 때 함께 쓴다. 순서는 사전 순(sort_order, id)
object TagRows {
    fun ofActivities(dsl: DSLContext, activityIds: Collection<Id>): Map<Id, List<Tag>> {
        if (activityIds.isEmpty()) return emptyMap()
        return dsl.select(ACTIVITY_TAGS.ACTIVITY_ID, TAGS.ID, TAGS.NAME, TAGS.NAME_EN)
            .from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .where(ACTIVITY_TAGS.ACTIVITY_ID.`in`(activityIds))
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch()
            .groupBy({ it.value1()!! }) { Tag(it.value2()!!, it.value3()!!, it.value4()) }
    }

    fun ofPrograms(dsl: DSLContext, programIds: Collection<Id>): Map<Id, List<Tag>> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(PROGRAM_TAGS.PROGRAM_ID, TAGS.ID, TAGS.NAME, TAGS.NAME_EN)
            .from(PROGRAM_TAGS).join(TAGS).on(TAGS.ID.eq(PROGRAM_TAGS.TAG_ID))
            .where(PROGRAM_TAGS.PROGRAM_ID.`in`(programIds))
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch()
            .groupBy({ it.value1()!! }) { Tag(it.value2()!!, it.value3()!!, it.value4()) }
    }

    // 검색어가 활동의 태그 이름(한글·영문)에 부분 일치하는 활동(ACTIVITIES 행 기준의 상관 조건)
    fun activityTagMatches(q: String): Condition = DSL.exists(
        DSL.selectOne().from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .where(
                ACTIVITY_TAGS.ACTIVITY_ID.eq(io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES.ID),
                TAGS.NAME.containsIgnoreCase(q).or(TAGS.NAME_EN.containsIgnoreCase(q)),
            )
    )

    // tagIds를 모두 가진 활동만(AND). 비어 있으면 조건 없음
    fun activityHasAllTags(tagIds: Collection<Id>): List<Condition> = tagIds.map { tagId ->
        io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES.ID.`in`(
            DSL.select(ACTIVITY_TAGS.ACTIVITY_ID).from(ACTIVITY_TAGS).where(ACTIVITY_TAGS.TAG_ID.eq(tagId))
        )
    }
}
