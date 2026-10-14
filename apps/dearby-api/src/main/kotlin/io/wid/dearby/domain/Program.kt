package io.wid.dearby.domain

data class Program(
    val id: Id,
    val organizationId: Id,
    val name: String,
    val description: String, // 카탈로그 원본 description · 사용: 없음
    val contacts: List<ProgramContact>,
    val period: Period,
    val timestamps: Timestamps,
)
