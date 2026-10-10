package io.wid.dearby.application

import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserId

// 저장소 포트. 구현은 adapter.persistence
interface UserRepository {
    fun findById(id: UserId): User?
}
