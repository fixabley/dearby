package io.wid.dearby.domain.user

import io.wid.dearby.domain.UserId

data class User(val id: UserId, val roles: List<UserRole>)