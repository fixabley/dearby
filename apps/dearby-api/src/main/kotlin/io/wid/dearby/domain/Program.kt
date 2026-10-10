package io.wid.dearby.domain

data class Program(
    val id: Id,
    val organizationId: Id,
    val name: String,
    val contacts: List<ProgramContact>,
    val period: Period,
    val timestamps: Timestamps,
)