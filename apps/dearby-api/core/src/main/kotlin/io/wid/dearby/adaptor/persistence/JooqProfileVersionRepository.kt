package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_VERSIONS
import io.wid.dearby.application.profile.ProfileVersionRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Profile
import io.wid.dearby.domain.profile.ProfileVersion
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 프로필 스냅샷을 JSON으로 둔다. 되돌릴 때 입력으로 쓰므로 이름·값·카탈로그 참조를 모두 남긴다(인증자료는 메타만)
@Repository
class JooqProfileVersionRepository(private val dsl: DSLContext) : ProfileVersionRepository {

    override fun append(profile: Profile) {
        val snapshot = Snapshot(profile.name, profile.job, profile.introduction, ProfileJson.contacts(profile.contacts), ProfileJson.histories(profile.histories))
        dsl.insertInto(PROFILE_VERSIONS)
            .set(PROFILE_VERSIONS.USER_ID, profile.userId)
            .set(PROFILE_VERSIONS.VERSION, profile.version)
            .set(PROFILE_VERSIONS.SNAPSHOT, JSONB.valueOf(ProfileJson.write(snapshot)))
            .set(PROFILE_VERSIONS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
    }

    override fun list(userId: UserId, after: Int?, size: Int): List<Pair<Int, OffsetDateTime>> =
        dsl.select(PROFILE_VERSIONS.VERSION, PROFILE_VERSIONS.CREATED_AT).from(PROFILE_VERSIONS)
            .where(PROFILE_VERSIONS.USER_ID.eq(userId), after?.let { PROFILE_VERSIONS.VERSION.lt(it) } ?: DSL.noCondition())
            .orderBy(PROFILE_VERSIONS.VERSION.desc()).limit(size).fetch()
            .map { it.value1()!! to it.value2()!! }

    override fun find(userId: UserId, version: Int): ProfileVersion? {
        val row = dsl.selectFrom(PROFILE_VERSIONS).where(PROFILE_VERSIONS.USER_ID.eq(userId), PROFILE_VERSIONS.VERSION.eq(version)).fetchOne() ?: return null
        val s = ProfileJson.read<Snapshot>(row.snapshot!!.data())
        val profile = Profile(userId, s.name, s.job, s.introduction, ProfileJson.readContacts(s.contacts), ProfileJson.readHistories(s.histories), version, row.createdAt!!)
        return ProfileVersion(version, row.createdAt!!, profile)
    }

    // contacts·histories는 ProfileJson 형식의 JSON 문자열
    data class Snapshot(val name: String, val job: String, val introduction: String, val contacts: String, val histories: String)
}
