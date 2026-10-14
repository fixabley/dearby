package io.wid.dearby.domain

data class Organization(
    val id: Id,
    val name: String,
    val description: String, // 카탈로그 원본 description · 사용: 없음
    val contacts: List<OrganizationContact>,
    val parentOrganizationId: Id?,
    val timestamps: Timestamps,
    val address: Address?, // 카탈로그 원본에 주소가 없어 비어 있을 수 있다
)
