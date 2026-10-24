package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_LOGOS
import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.domain.BrandColor
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramLogo
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqProgramLogoRepository(private val dsl: DSLContext) : ProgramLogoRepository {

    override fun find(programId: Id): ProgramLogo? =
        dsl.selectFrom(PROGRAM_LOGOS).where(PROGRAM_LOGOS.PROGRAM_ID.eq(programId)).fetchOne()
            ?.let { ProgramLogo(it.programId!!, it.contentType!!, it.data!!, it.updatedAt!!) }

    override fun updatedAtByProgram(programIds: Collection<Id>): Map<Id, OffsetDateTime> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(PROGRAM_LOGOS.PROGRAM_ID, PROGRAM_LOGOS.UPDATED_AT)
            .from(PROGRAM_LOGOS)
            .where(PROGRAM_LOGOS.PROGRAM_ID.`in`(programIds))
            .fetch { it.value1()!! to it.value2()!! }
            .toMap()
    }

    override fun save(logo: ProgramLogo) {
        dsl.insertInto(PROGRAM_LOGOS)
            .set(PROGRAM_LOGOS.PROGRAM_ID, logo.programId)
            .set(PROGRAM_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(PROGRAM_LOGOS.DATA, logo.data)
            .set(PROGRAM_LOGOS.UPDATED_AT, logo.updatedAt)
            .set(PROGRAM_LOGOS.BRAND_COLOR, BrandColor.of(logo.data))
            .onConflict(PROGRAM_LOGOS.PROGRAM_ID)
            .doUpdate()
            .set(PROGRAM_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(PROGRAM_LOGOS.DATA, logo.data)
            .set(PROGRAM_LOGOS.UPDATED_AT, logo.updatedAt)
            .set(PROGRAM_LOGOS.BRAND_COLOR, BrandColor.of(logo.data))
            .execute()
    }

    override fun delete(programId: Id) {
        dsl.deleteFrom(PROGRAM_LOGOS).where(PROGRAM_LOGOS.PROGRAM_ID.eq(programId)).execute()
    }

    override fun brandColors(ids: Collection<Id>): Map<Id, String> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(PROGRAM_LOGOS.PROGRAM_ID, PROGRAM_LOGOS.BRAND_COLOR).from(PROGRAM_LOGOS)
            .where(PROGRAM_LOGOS.PROGRAM_ID.`in`(ids), PROGRAM_LOGOS.BRAND_COLOR.isNotNull)
            .fetch { it.value1()!! to it.value2()!! }.toMap()
    }

    // 대표색이 없는 로고를 다시 계산한다(V52 이전에 올린 로고). 색을 찾은 수
    override fun backfillBrandColors(): Int =
        dsl.select(PROGRAM_LOGOS.PROGRAM_ID, PROGRAM_LOGOS.DATA).from(PROGRAM_LOGOS).where(PROGRAM_LOGOS.BRAND_COLOR.isNull).fetch().count { row ->
            BrandColor.of(row.value2()!!)?.let { color -> dsl.update(PROGRAM_LOGOS).set(PROGRAM_LOGOS.BRAND_COLOR, color).where(PROGRAM_LOGOS.PROGRAM_ID.eq(row.value1())).execute() > 0 } == true
        }
}
