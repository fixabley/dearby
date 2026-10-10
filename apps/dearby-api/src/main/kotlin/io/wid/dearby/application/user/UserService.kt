package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.NotFoundException
import org.springframework.stereotype.Service

@Service
class UserService(private val users: UserRepository) {

    fun getById(id: UserId): User = users.findById(id) ?: throw NotFoundException("사용자를 찾을 수 없습니다", "User not found: $id")
}
