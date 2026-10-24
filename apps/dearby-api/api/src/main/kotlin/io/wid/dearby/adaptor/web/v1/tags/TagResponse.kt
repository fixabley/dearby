package io.wid.dearby.adaptor.web.v1.tags

import io.wid.dearby.domain.Tag

// 태그(native-v1.md "태그(얻을 수 있는 것)", "분야·성격 태그와 아이콘"): 사전 id, 한글·영문 이름, 종류(FIELD 분야 | TRAIT 성격), 상위 분야 id
data class TagResponse(val id: Long, val name: String, val nameEn: String?, val kind: String, val parentId: Long?) {
    companion object {
        fun from(tag: Tag) = TagResponse(tag.id, tag.name, tag.nameEn, tag.kind.name, tag.parentId)
    }
}

data class TagListResponse(val items: List<TagResponse>)
