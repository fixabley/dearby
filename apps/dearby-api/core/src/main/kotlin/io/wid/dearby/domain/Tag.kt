package io.wid.dearby.domain

// 태그(native-v1.md "태그(얻을 수 있는 것)"): 참가자가 얻는 것을 나타내는 통제 어휘 한 항목. 종류(category)가 "무엇인가"라면 태그는 "무엇을 얻는가"
data class Tag(
    val id: Id,
    val name: String, // 한글 이름
    val nameEn: String?, // 영문 이름. 어드민이 직접 입력해 만든 태그는 비어 있을 수 있다
)
