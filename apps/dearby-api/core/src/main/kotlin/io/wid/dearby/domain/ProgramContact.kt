package io.wid.dearby.domain

data class ProgramContact(
    val id: Id,
    val programId: Id,
    val kind: ContactKind,
    val label: String,
    val value: String,
)
