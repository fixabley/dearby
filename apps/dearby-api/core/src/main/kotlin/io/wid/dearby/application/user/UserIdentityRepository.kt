package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId

// 외부 로그인 계정(provider, subject)과 사용자의 연결. 구현은 adaptor/persistence
interface UserIdentityRepository {

    fun findUserId(provider: String, subject: String): UserId?

    fun existsForUser(provider: String, userId: UserId): Boolean

    fun findAllByUser(userId: UserId): List<StoredIdentity>

    // (provider, subject)가 이미 있으면 DuplicateKeyException
    fun create(provider: String, subject: String, userId: UserId, email: String?, displayName: String?, clientId: String?, refreshToken: String?)

    // 다시 로그인하며 새로 받은 애플 토큰으로 바꾼다
    fun updateToken(provider: String, subject: String, clientId: String, refreshToken: String)

    // 그 사용자에게 연결된 provider 계정을 끊는다. 없었으면 false
    fun delete(provider: String, userId: UserId): Boolean
}
