package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User

// 저장소 포트. 구현은 adapter.persistence
interface UserRepository {
    fun findById(id: UserId): User?

    fun create(user: User)
}
