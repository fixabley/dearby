package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.GITHUB_CONTACT_LINKS
import io.wid.dearby.application.profile.GitHubContactLink
import io.wid.dearby.application.profile.GitHubContactLinkRepository
import io.wid.dearby.application.profile.GitHubContactLinkRepository.Companion.TTL
import io.wid.dearby.application.profile.GitHubLinkStep
import io.wid.dearby.application.profile.LinkPlatform
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqGitHubContactLinkRepository(private val dsl: DSLContext) : GitHubContactLinkRepository {

    override fun issue(step: GitHubLinkStep, link: GitHubContactLink): UUID {
        val now = now()
        // 쓰지 않고 만료된 상태를 함께 정리한다
        dsl.deleteFrom(GITHUB_CONTACT_LINKS).where(GITHUB_CONTACT_LINKS.EXPIRES_AT.le(now)).execute()
        val id = UUID.randomUUID()
        dsl.insertInto(GITHUB_CONTACT_LINKS)
            .set(GITHUB_CONTACT_LINKS.ID, id)
            .set(GITHUB_CONTACT_LINKS.USER_ID, link.userId)
            .set(GITHUB_CONTACT_LINKS.STEP, step.name)
            .set(GITHUB_CONTACT_LINKS.PLATFORM, link.platform.name.lowercase())
            .set(GITHUB_CONTACT_LINKS.LOGIN, link.login)
            .set(GITHUB_CONTACT_LINKS.EXPIRES_AT, now.plus(TTL))
            .execute()
        return id
    }

    // 조건부 delete라 같은 id로 동시에 요청해도 한 번만 성공
    override fun consume(id: UUID, step: GitHubLinkStep): GitHubContactLink? =
        dsl.deleteFrom(GITHUB_CONTACT_LINKS)
            .where(GITHUB_CONTACT_LINKS.ID.eq(id), GITHUB_CONTACT_LINKS.STEP.eq(step.name), GITHUB_CONTACT_LINKS.EXPIRES_AT.gt(now()))
            .returning()
            .fetchOne()
            ?.let { GitHubContactLink(it.userId!!, LinkPlatform.valueOf(it.platform!!.uppercase()), it.login) }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
}
