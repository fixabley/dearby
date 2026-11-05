package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.DiscordAccountsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.records.DiscordGuildConnectionsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISCORD_ACCOUNTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISCORD_GUILD_CONNECTIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISCORD_OAUTH_STATES
import io.wid.dearby.application.discord.DiscordAccountRepository
import io.wid.dearby.application.discord.DiscordFlow
import io.wid.dearby.application.discord.DiscordGuildConnection
import io.wid.dearby.application.discord.DiscordGuildConnectionRepository
import io.wid.dearby.application.discord.DiscordOAuthState
import io.wid.dearby.application.discord.DiscordOAuthStateRepository
import io.wid.dearby.application.discord.DiscordOAuthStateRepository.Companion.TTL
import io.wid.dearby.application.discord.DiscordOAuthStep
import io.wid.dearby.application.discord.StoredDiscordAccount
import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqDiscordOAuthStateRepository(private val dsl: DSLContext) : DiscordOAuthStateRepository {

    override fun issue(state: DiscordOAuthState): UUID {
        val now = now()
        // 쓰지 않고 만료된 상태를 함께 정리한다
        dsl.deleteFrom(DISCORD_OAUTH_STATES).where(DISCORD_OAUTH_STATES.EXPIRES_AT.le(now)).execute()
        val id = UUID.randomUUID()
        dsl.insertInto(DISCORD_OAUTH_STATES)
            .set(DISCORD_OAUTH_STATES.ID, id)
            .set(DISCORD_OAUTH_STATES.USER_ID, state.userId)
            .set(DISCORD_OAUTH_STATES.FLOW, state.flow.name)
            .set(DISCORD_OAUTH_STATES.STEP, state.step.name)
            .set(DISCORD_OAUTH_STATES.PLATFORM, state.platform.name.lowercase())
            .set(DISCORD_OAUTH_STATES.DISCORD_CODE, state.discordCode)
            .set(DISCORD_OAUTH_STATES.EXPIRES_AT, now.plus(TTL))
            .execute()
        return id
    }

    // 조건부 delete라 같은 id로 동시에 요청해도 한 번만 성공
    override fun consume(id: UUID, flow: DiscordFlow, step: DiscordOAuthStep): DiscordOAuthState? =
        dsl.deleteFrom(DISCORD_OAUTH_STATES)
            .where(
                DISCORD_OAUTH_STATES.ID.eq(id), DISCORD_OAUTH_STATES.FLOW.eq(flow.name),
                DISCORD_OAUTH_STATES.STEP.eq(step.name), DISCORD_OAUTH_STATES.EXPIRES_AT.gt(now()),
            )
            .returning()
            .fetchOne()
            ?.let { DiscordOAuthState(it.userId!!, flow, step, LinkPlatform.valueOf(it.platform!!.uppercase()), it.discordCode) }
}

@Repository
class JooqDiscordAccountRepository(private val dsl: DSLContext) : DiscordAccountRepository {

    override fun find(userId: UserId): StoredDiscordAccount? =
        dsl.selectFrom(DISCORD_ACCOUNTS).where(DISCORD_ACCOUNTS.USER_ID.eq(userId)).fetchOne()?.let(::toAccount)

    override fun findForUpdate(userId: UserId): StoredDiscordAccount? =
        dsl.selectFrom(DISCORD_ACCOUNTS).where(DISCORD_ACCOUNTS.USER_ID.eq(userId)).forUpdate().fetchOne()?.let(::toAccount)

    override fun findOwner(discordUserId: String): UserId? =
        dsl.select(DISCORD_ACCOUNTS.USER_ID).from(DISCORD_ACCOUNTS).where(DISCORD_ACCOUNTS.DISCORD_USER_ID.eq(discordUserId)).fetchOne(DISCORD_ACCOUNTS.USER_ID)

    override fun save(account: StoredDiscordAccount) {
        dsl.insertInto(DISCORD_ACCOUNTS)
            .set(DISCORD_ACCOUNTS.USER_ID, account.userId)
            .set(DISCORD_ACCOUNTS.DISCORD_USER_ID, account.discordUserId)
            .set(DISCORD_ACCOUNTS.USERNAME, account.username)
            .set(DISCORD_ACCOUNTS.ACCESS_TOKEN_CIPHERTEXT, account.accessToken)
            .set(DISCORD_ACCOUNTS.REFRESH_TOKEN_CIPHERTEXT, account.refreshToken)
            .set(DISCORD_ACCOUNTS.TOKEN_EXPIRES_AT, account.tokenExpiresAt)
            .set(DISCORD_ACCOUNTS.LINKED_AT, account.linkedAt)
            .onConflict(DISCORD_ACCOUNTS.USER_ID).doUpdate()
            .set(DISCORD_ACCOUNTS.DISCORD_USER_ID, account.discordUserId)
            .set(DISCORD_ACCOUNTS.USERNAME, account.username)
            .set(DISCORD_ACCOUNTS.ACCESS_TOKEN_CIPHERTEXT, account.accessToken)
            .set(DISCORD_ACCOUNTS.REFRESH_TOKEN_CIPHERTEXT, account.refreshToken)
            .set(DISCORD_ACCOUNTS.TOKEN_EXPIRES_AT, account.tokenExpiresAt)
            .set(DISCORD_ACCOUNTS.LINKED_AT, account.linkedAt)
            .execute()
    }

    override fun updateTokens(userId: UserId, accessToken: ByteArray, refreshToken: ByteArray, expiresAt: OffsetDateTime) {
        dsl.update(DISCORD_ACCOUNTS)
            .set(DISCORD_ACCOUNTS.ACCESS_TOKEN_CIPHERTEXT, accessToken)
            .set(DISCORD_ACCOUNTS.REFRESH_TOKEN_CIPHERTEXT, refreshToken)
            .set(DISCORD_ACCOUNTS.TOKEN_EXPIRES_AT, expiresAt)
            .where(DISCORD_ACCOUNTS.USER_ID.eq(userId))
            .execute()
    }

    override fun delete(userId: UserId): Boolean =
        dsl.deleteFrom(DISCORD_ACCOUNTS).where(DISCORD_ACCOUNTS.USER_ID.eq(userId)).execute() > 0

    private fun toAccount(r: DiscordAccountsRecord) = StoredDiscordAccount(
        r.userId!!, r.discordUserId!!, r.username!!, r.accessTokenCiphertext!!, r.refreshTokenCiphertext!!, r.tokenExpiresAt!!, r.linkedAt!!,
    )
}

@Repository
class JooqDiscordGuildConnectionRepository(private val dsl: DSLContext) : DiscordGuildConnectionRepository {

    override fun upsert(userId: UserId, guildId: String, guildName: String): DiscordGuildConnection {
        val now = now()
        return dsl.insertInto(DISCORD_GUILD_CONNECTIONS)
            .set(DISCORD_GUILD_CONNECTIONS.ID, UUID.randomUUID())
            .set(DISCORD_GUILD_CONNECTIONS.USER_ID, userId)
            .set(DISCORD_GUILD_CONNECTIONS.GUILD_ID, guildId)
            .set(DISCORD_GUILD_CONNECTIONS.GUILD_NAME, guildName)
            .set(DISCORD_GUILD_CONNECTIONS.CREATED_AT, now)
            .set(DISCORD_GUILD_CONNECTIONS.UPDATED_AT, now)
            .onConflict(DISCORD_GUILD_CONNECTIONS.USER_ID, DISCORD_GUILD_CONNECTIONS.GUILD_ID).doUpdate()
            .set(DISCORD_GUILD_CONNECTIONS.GUILD_NAME, guildName)
            .set(DISCORD_GUILD_CONNECTIONS.UPDATED_AT, now)
            .returning()
            .fetchSingle()
            .let(::toConnection)
    }

    override fun find(id: UUID): DiscordGuildConnection? =
        dsl.selectFrom(DISCORD_GUILD_CONNECTIONS).where(DISCORD_GUILD_CONNECTIONS.ID.eq(id)).fetchOne()?.let(::toConnection)

    override fun findAllByUser(userId: UserId): List<DiscordGuildConnection> =
        dsl.selectFrom(DISCORD_GUILD_CONNECTIONS).where(DISCORD_GUILD_CONNECTIONS.USER_ID.eq(userId))
            .orderBy(DISCORD_GUILD_CONNECTIONS.CREATED_AT.desc()).fetch().map(::toConnection)

    override fun delete(userId: UserId, id: UUID): Boolean =
        dsl.deleteFrom(DISCORD_GUILD_CONNECTIONS).where(DISCORD_GUILD_CONNECTIONS.ID.eq(id), DISCORD_GUILD_CONNECTIONS.USER_ID.eq(userId)).execute() > 0

    private fun toConnection(r: DiscordGuildConnectionsRecord) =
        DiscordGuildConnection(r.id!!, r.userId!!, r.guildId!!, r.guildName!!, r.createdAt!!)
}

private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
