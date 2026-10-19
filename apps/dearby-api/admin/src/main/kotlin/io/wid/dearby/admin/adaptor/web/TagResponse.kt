package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.Tag

// 태그(native-v1.md "태그"): 사전 id와 한글·영문 이름. 공개 API의 TagResponse와 같은 형태
data class TagResponse(val id: Long, val name: String, val nameEn: String?) {
    companion object {
        fun from(tag: Tag) = TagResponse(tag.id, tag.name, tag.nameEn)
    }
}

data class TagListResponse(val items: List<TagResponse>)

// POST는 name 필수(띄어쓰기만 다른 같은 이름이 있으면 그 태그를 돌려준다), PATCH는 보낸 값만(nameEn 빈 문자열은 삭제)
data class TagRequest(val name: String? = null, val nameEn: String? = null)
