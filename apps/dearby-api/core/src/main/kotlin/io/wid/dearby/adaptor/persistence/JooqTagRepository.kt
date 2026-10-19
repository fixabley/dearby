package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Tag
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JooqTagRepository(private val dsl: DSLContext) : TagRepository {

    override fun findAll(): List<Tag> =
        dsl.selectFrom(TAGS).orderBy(TAGS.SORT_ORDER, TAGS.ID).fetch { Tag(it.id!!, it.name!!, it.nameEn) }

    override fun findAllById(ids: Collection<Id>): List<Tag> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(TAGS).where(TAGS.ID.`in`(ids)).orderBy(TAGS.SORT_ORDER, TAGS.ID).fetch { Tag(it.id!!, it.name!!, it.nameEn) }
    }

    override fun findByNames(names: Collection<String>): List<Tag> {
        val wanted = names.map(::normalize).filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return dsl.selectFrom(TAGS)
            .where(DSL.lower(withoutSpaces(TAGS.NAME)).`in`(wanted).or(DSL.lower(withoutSpaces(TAGS.NAME_EN)).`in`(wanted)))
            .orderBy(TAGS.SORT_ORDER, TAGS.ID)
            .fetch { Tag(it.id!!, it.name!!, it.nameEn) }
    }

    @Transactional
    override fun create(name: String, nameEn: String?): Tag {
        dsl.selectFrom(TAGS).where(DSL.lower(withoutSpaces(TAGS.NAME)).eq(normalize(name))).fetchOne()?.let { return Tag(it.id!!, it.name!!, it.nameEn) }
        // 새 태그는 사전 맨 뒤에 둔다
        val nextOrder = dsl.select(DSL.coalesce(DSL.max(TAGS.SORT_ORDER), 0).plus(1)).from(TAGS).fetchOne()!!.value1()
        val row = dsl.insertInto(TAGS)
            .set(TAGS.NAME, name)
            .set(TAGS.NAME_EN, nameEn)
            .set(TAGS.SORT_ORDER, nextOrder)
            .returning(TAGS.ID, TAGS.NAME, TAGS.NAME_EN)
            .fetchOne()!!
        return Tag(row.id!!, row.name!!, row.nameEn)
    }

    override fun update(id: Id, name: String?, nameEn: String?): Boolean {
        val changes = buildMap<Field<*>, Any?> {
            name?.let { put(TAGS.NAME, it) }
            nameEn?.let { put(TAGS.NAME_EN, it.ifEmpty { null }) }
        }
        if (changes.isEmpty()) return dsl.fetchExists(TAGS, TAGS.ID.eq(id))
        return dsl.update(TAGS).set(changes).where(TAGS.ID.eq(id)).execute() > 0
    }

    override fun delete(id: Id): Boolean = dsl.deleteFrom(TAGS).where(TAGS.ID.eq(id)).execute() > 0

    @Transactional
    override fun setProgramTags(programId: Id, tagIds: Collection<Id>) {
        dsl.deleteFrom(PROGRAM_TAGS).where(PROGRAM_TAGS.PROGRAM_ID.eq(programId)).execute()
        tagIds.toSet().forEach { dsl.insertInto(PROGRAM_TAGS).set(PROGRAM_TAGS.PROGRAM_ID, programId).set(PROGRAM_TAGS.TAG_ID, it).execute() }
    }

    @Transactional
    override fun setActivityTags(activityId: Id, tagIds: Collection<Id>, curate: Boolean) {
        dsl.deleteFrom(ACTIVITY_TAGS).where(ACTIVITY_TAGS.ACTIVITY_ID.eq(activityId)).execute()
        tagIds.toSet().forEach { dsl.insertInto(ACTIVITY_TAGS).set(ACTIVITY_TAGS.ACTIVITY_ID, activityId).set(ACTIVITY_TAGS.TAG_ID, it).execute() }
        if (curate) {
            val curated = dsl.select(ACTIVITIES.CURATED_FIELDS).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()?.value1()
            dsl.update(ACTIVITIES)
                .set(ACTIVITIES.CURATED_FIELDS, CuratedFields.jsonb(CuratedFields.read(curated) + CuratedFields.TAGS))
                .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
                .where(ACTIVITIES.ID.eq(activityId))
                .execute()
        }
    }

    @Transactional
    override fun addProgramTagsToActivities(programId: Id, tagIds: Collection<Id>): Int {
        if (tagIds.isEmpty()) return 0
        val targets = dsl.select(ACTIVITIES.ID).from(ACTIVITIES)
            .where(ACTIVITIES.PROGRAM_ID.eq(programId), DSL.condition("not jsonb_exists({0}, 'tags')", ACTIVITIES.CURATED_FIELDS))
            .fetch(ACTIVITIES.ID)
        var touched = 0
        targets.forEach { activityId ->
            val added = tagIds.toSet().sumOf { tagId ->
                dsl.insertInto(ACTIVITY_TAGS).set(ACTIVITY_TAGS.ACTIVITY_ID, activityId).set(ACTIVITY_TAGS.TAG_ID, tagId).onConflictDoNothing().execute()
            }
            if (added > 0) touched++
        }
        return touched
    }

    private fun normalize(name: String) = name.replace(Regex("\\s"), "").lowercase()
}
