package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.TagsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Tag
import io.wid.dearby.domain.TagKind
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

// 태그 연결 읽기(native-v1.md "태그"). 활동·프로그램 저장소가 목록을 채울 때 함께 쓴다. 순서는 사전 순(sort_order, id)
object TagRows {
    val FIELDS = listOf(TAGS.ID, TAGS.NAME, TAGS.NAME_EN, TAGS.KIND, TAGS.PARENT_ID)
    private val parent = TAGS.`as`("parent")

    fun tag(row: Record) = Tag(row[TAGS.ID]!!, row[TAGS.NAME]!!, row[TAGS.NAME_EN], TagKind.valueOf(row[TAGS.KIND]!!), row[TAGS.PARENT_ID])
    fun tag(row: TagsRecord) = Tag(row.id!!, row.name!!, row.nameEn, TagKind.valueOf(row.kind!!), row.parentId)

    // 활동 태그 전부(사전 순)와 분야 태그 순서(연관도 내림차순 → 사전 순, 하위 분야가 있는 상위 분야는 뺀다)
    fun ofActivities(dsl: DSLContext, activityIds: Collection<Id>): Map<Id, Pair<List<Tag>, List<Tag>>> {
        if (activityIds.isEmpty()) return emptyMap()
        return dsl.select(FIELDS + ACTIVITY_TAGS.ACTIVITY_ID + ACTIVITY_TAGS.RELEVANCE)
            .from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .where(ACTIVITY_TAGS.ACTIVITY_ID.`in`(activityIds))
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch()
            .groupBy { it[ACTIVITY_TAGS.ACTIVITY_ID]!! }
            .mapValues { (_, rows) ->
                val tags = rows.map(::tag)
                tags to orderFields(rows.filter { it[TAGS.KIND] == TagKind.FIELD.name }.map { RankedField(tag(it), it[ACTIVITY_TAGS.RELEVANCE]?.toDouble(), null) })
            }
    }

    // 운영자가 적은 순서(position)대로. 같은 position은 사전 순. 어드민 편집 폼이 이 순서로 다시 저장한다
    fun ofPrograms(dsl: DSLContext, programIds: Collection<Id>): Map<Id, List<Tag>> =
        programRows(dsl, programIds).mapValues { (_, rows) -> rows.sortedBy { it.second }.map { it.first } }

    // 프로그램 태그와 position, 사전 순
    fun programRows(dsl: DSLContext, programIds: Collection<Id>): Map<Id, List<Pair<Tag, Int>>> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(FIELDS + PROGRAM_TAGS.PROGRAM_ID + PROGRAM_TAGS.POSITION)
            .from(PROGRAM_TAGS).join(TAGS).on(TAGS.ID.eq(PROGRAM_TAGS.TAG_ID))
            .where(PROGRAM_TAGS.PROGRAM_ID.`in`(programIds))
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch()
            .groupBy({ it[PROGRAM_TAGS.PROGRAM_ID]!! }) { tag(it) to it[PROGRAM_TAGS.POSITION]!!.toInt() }
    }

    // 분야 태그 후보 한 개: 연관도(활동 연관도 또는 그 평균, 없으면 null)와 운영자 순서(없으면 null)
    data class RankedField(val tag: Tag, val relevance: Double?, val position: Int?)

    // 분야 태그 순서(native-v1.md "분야·성격 태그와 아이콘"): 연관도 내림차순 → 연관도 없는 태그는 position 순 → 들어온 순서(사전 순).
    // 하위 분야가 함께 있는 상위 분야는 하위 분야가 함의하므로 뺀다
    fun orderFields(candidates: List<RankedField>): List<Tag> {
        val parents = candidates.mapNotNull { it.tag.parentId }.toSet()
        return candidates.filter { it.tag.id !in parents }
            .sortedWith(compareBy<RankedField, Double?>(nullsLast(reverseOrder())) { it.relevance }.thenBy(nullsLast()) { it.position })
            .map { it.tag }
    }

    // 검색어가 활동의 태그 이름(한글·영문)이나 그 상위 분야 이름에 부분 일치하는 활동(ACTIVITIES 행 기준의 상관 조건)
    fun activityTagMatches(q: String): Condition = DSL.exists(
        DSL.selectOne().from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .leftJoin(parent).on(parent.ID.eq(TAGS.PARENT_ID))
            .where(
                ACTIVITY_TAGS.ACTIVITY_ID.eq(ACTIVITIES.ID),
                DSL.or(listOf(TAGS.NAME, TAGS.NAME_EN, parent.NAME, parent.NAME_EN).map { it.containsIgnoreCase(q) }),
            )
    )

    // tagIds를 모두 가진 활동만(AND). 상위 분야 id는 그 하위 분야 중 하나를 가진 활동도 맞는다. 비어 있으면 조건 없음
    fun activityHasAllTags(tagIds: Collection<Id>): List<Condition> = tagIds.map { tagId ->
        ACTIVITIES.ID.`in`(
            DSL.select(ACTIVITY_TAGS.ACTIVITY_ID).from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
                .where(TAGS.ID.eq(tagId).or(TAGS.PARENT_ID.eq(tagId)))
        )
    }
}
