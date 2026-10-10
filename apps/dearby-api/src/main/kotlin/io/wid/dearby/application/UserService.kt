package io.wid.dearby.application

import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service

@Service
class UserService(private val users: UserRepository) {

    fun getById(id: UserId): User = users.findById(id) ?: throw UserNotFoundException(id)
}
