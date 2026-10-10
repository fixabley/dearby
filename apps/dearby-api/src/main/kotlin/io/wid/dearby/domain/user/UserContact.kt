package io.wid.dearby.domain.user

import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId


data class UserContact(
    val id: Id,
    val userId: UserId,
    val kind: ContactKind,
    val label: String,
    val value: String,
)
