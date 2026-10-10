package io.wid.dearby.application

import io.wid.dearby.domain.UserId

class UserNotFoundException(val id: UserId) : RuntimeException("User not found: $id")
