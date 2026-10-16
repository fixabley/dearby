package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId

// 외부 로그인 계정(provider, subject)과 사용자의 연결. 구현은 adaptor/persistence
interface UserIdentityRepository {

    fun findUserId(provider: String, subject: String): UserId?

    fun existsForUser(provider: String, userId: UserId): Boolean

    // (provider, subject)가 이미 있으면 DuplicateKeyException
    fun create(provider: String, subject: String, userId: UserId, email: String?, displayName: String?)
}
