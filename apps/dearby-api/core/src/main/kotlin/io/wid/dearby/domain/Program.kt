package io.wid.dearby.domain

data class Program(
    val id: Id,
    val organizationId: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름(없으면 null)
    val contacts: List<ProgramContact>,
    val period: Period,
    val timestamps: Timestamps,
)
