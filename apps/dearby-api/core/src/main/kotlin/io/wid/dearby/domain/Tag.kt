package io.wid.dearby.domain

// 태그(native-v1.md "태그(얻을 수 있는 것)", "분야·성격 태그와 아이콘"): 통제 어휘 한 항목. 종류(category)가 "무엇인가"라면
// 분야 태그는 "무엇을 하나", 성격 태그는 "무엇을 얻나"
data class Tag(
    val id: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름. 어드민이 직접 입력해 만든 태그는 비어 있을 수 있다
    val kind: TagKind,
    val parentId: Id? = null, // 하위 분야의 상위 분야(한 단계, FIELD끼리만). 상위 분야는 하위 분야가 함의한다
)

enum class TagKind { FIELD, TRAIT }
