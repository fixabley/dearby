package io.wid.dearby.domain

class Profile(val id: Id, val userId: UserId, val name: String, val contacts: List<UserContact>, timestamps: Timestamps)
