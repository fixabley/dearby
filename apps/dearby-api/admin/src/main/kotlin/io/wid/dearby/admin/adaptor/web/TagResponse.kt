package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.Tag
import tools.jackson.databind.JsonNode

// 태그(native-v1.md "태그", "분야·성격 태그와 아이콘"): 사전 id, 한글·영문 이름, 종류, 상위 분야 id. 공개 API의 TagResponse와 같은 형태
data class TagResponse(val id: Long, val name: String, val nameEn: String?, val kind: String, val parentId: Long?) {
    companion object {
        fun from(tag: Tag) = TagResponse(tag.id, tag.name, tag.nameEn, tag.kind.name, tag.parentId)
    }
}

data class TagListResponse(val items: List<TagResponse>)

// POST는 name 필수(띄어쓰기만 다른 같은 이름이 있으면 그 태그를 돌려준다), PATCH는 보낸 값만(nameEn 빈 문자열은 삭제).
// kind: FIELD | TRAIT(POST 기본 TRAIT). parentId: 상위 분야 id, PATCH에서 생략은 유지·null은 해제
data class TagRequest(val name: String? = null, val nameEn: String? = null, val kind: String? = null, val parentId: JsonNode? = null)
