package io.wid.dearby.domain

data class Program(
    val id: Id,
    val organizationId: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름(없으면 null)
    val contacts: List<ProgramContact>,
    val period: Period,
    val timestamps: Timestamps,
    val category: ActivityCategory? = null, // 기본 활동 종류(admin-v1.md "프로그램 기본 종류"). 수집·운영자 생성 활동의 기본값
    val tags: List<Tag> = emptyList(), // 프로그램 태그(native-v1.md "태그"). 수집·운영자 생성 활동의 기본값이고 일괄 적용에 쓴다
)
