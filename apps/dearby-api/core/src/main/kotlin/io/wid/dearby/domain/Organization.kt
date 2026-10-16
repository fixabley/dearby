package io.wid.dearby.domain

data class Organization(
    val id: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름(없으면 null)
    val contacts: List<OrganizationContact>,
    val parentOrganizationId: Id?,
    val timestamps: Timestamps,
    val address: Address?, // 활동 정보 원본에 주소가 없어 비어 있을 수 있다
)
