package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import java.time.OffsetDateTime

// 저장소 포트. 구현은 adapter.persistence
interface UserRepository {
    fun findById(id: UserId): User?

    // 가입 시각(users.created_at). 없는 사용자면 null
    fun findCreatedAt(id: UserId): OffsetDateTime?

    fun create(user: User)

    // 사용자와 그 사용자의 모든 행(역할·연락처·프로필·명함·이력·참여·refresh token·패스키·외부 로그인 연결)을 지운다
    fun delete(id: UserId)
}
