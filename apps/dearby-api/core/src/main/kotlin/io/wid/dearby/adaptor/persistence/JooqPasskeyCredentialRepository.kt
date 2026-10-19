package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.UserCredentialsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_CREDENTIALS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ENTITIES
import io.wid.dearby.application.auth.PasskeyCredentialRepository
import io.wid.dearby.application.auth.StoredPasskey
import io.wid.dearby.application.auth.userHandle
import io.wid.dearby.domain.UserId
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneId

@Repository
class JooqPasskeyCredentialRepository(private val dsl: DSLContext) : PasskeyCredentialRepository {

    override fun findAllByUser(userId: UserId): List<StoredPasskey> =
        dsl.selectFrom(USER_CREDENTIALS).where(ownedBy(userId)).orderBy(USER_CREDENTIALS.CREATED).fetch().map(::toStored)

    override fun rename(userId: UserId, credentialId: String, label: String): StoredPasskey? =
        dsl.update(USER_CREDENTIALS).set(USER_CREDENTIALS.LABEL, label)
            .where(USER_CREDENTIALS.CREDENTIAL_ID.eq(credentialId), ownedBy(userId))
            .returning().fetchOne()?.let(::toStored)

    override fun delete(userId: UserId, credentialId: String): Boolean =
        dsl.deleteFrom(USER_CREDENTIALS).where(USER_CREDENTIALS.CREDENTIAL_ID.eq(credentialId), ownedBy(userId)).execute() == 1

    // user handle 또는 2026-10-15 이전 가입분의 name(UUID 문자열)으로 그 사용자의 user entity를 찾는다(JooqUserRepository.delete와 같은 규칙)
    private fun ownedBy(userId: UserId): Condition =
        USER_CREDENTIALS.USER_ENTITY_USER_ID.`in`(
            dsl.select(USER_ENTITIES.ID).from(USER_ENTITIES)
                .where(USER_ENTITIES.ID.eq(userHandle(userId)).or(USER_ENTITIES.NAME.eq(userId.toString())))
        )

    // created·last_used는 시간대 없는 열이고 Spring Security가 JVM 시간대로 쓰므로 같은 시간대로 읽는다. transports는 쉼표 구분(없으면 빈 문자열)
    private fun toStored(record: UserCredentialsRecord) = StoredPasskey(
        credentialId = record.credentialId!!,
        label = record.label!!,
        createdAt = record.created!!.toOffset(),
        lastUsedAt = (record.lastUsed ?: record.created!!).toOffset(),
        backedUp = record.backupState!!,
        transports = record.authenticatorTransports.orEmpty().split(',').filter { it.isNotBlank() },
    )

    private fun LocalDateTime.toOffset() = atZone(ZoneId.systemDefault()).toOffsetDateTime()
}
