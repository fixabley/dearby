package com.dearby.nativeapp.entities.activity.model

/** Contract "분야·성격 태그와 아이콘" (2026-10-24): 분야(무엇을 하나) or 성격(무엇을 얻나). */
enum class TagKind { FIELD, TRAIT }

/**
 * One entry of the operator-managed tag dictionary (contract "태그(얻을 수 있는 것)", 2026-10-19; kinds 2026-10-24), e.g. 데이터·AI (분야) or
 * 창업·멘토링 (성격). [id] is the API's number as text; [nameEn] may be null until an operator fills it and picks the icon; a missing [kind] is 성격.
 */
data class Tag(val id: String, val name: String, val nameEn: String?, val kind: TagKind = TagKind.TRAIT)
