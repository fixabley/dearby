package io.wid.dearby.adaptor.web.v1.tags

import io.wid.dearby.domain.Tag

// 태그(native-v1.md "태그(얻을 수 있는 것)"): 사전 id와 한글·영문 이름
data class TagResponse(val id: Long, val name: String, val nameEn: String?) {
    companion object {
        fun from(tag: Tag) = TagResponse(tag.id, tag.name, tag.nameEn)
    }
}

data class TagListResponse(val items: List<TagResponse>)
