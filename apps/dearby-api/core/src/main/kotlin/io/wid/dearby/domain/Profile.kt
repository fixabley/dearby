package io.wid.dearby.domain

import io.wid.dearby.domain.user.UserContact

class Profile(val id: Id, val userId: UserId, val name: String, val contacts: List<UserContact>, timestamps: Timestamps)
