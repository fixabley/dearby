package io.wid.dearby.domain

data class ProgramStaff(val id: Id, val userId: UserId, val programId: Id, val roles: List<Id>, val description: String)
