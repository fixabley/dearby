package io.wid.dearby.adaptor.persistence

import org.jooq.JSONB
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// activities.inherited_fields: 이전 기수에서 가져온 필드 이름 배열(admin-v1.md "이전 기수 정보 자동완성")
object InheritedFields {
    const val ELIGIBILITY = "eligibility"
    const val AUDIENCE = "audience"
    const val PARTICIPATION_TYPE = "participationType"

    fun read(value: JSONB?): Set<String> = FieldNames.read(value)
    fun jsonb(fields: Collection<String>): JSONB = FieldNames.jsonb(fields)
}

// activities.curated_fields: 어드민 PATCH로 바꾼 필드 이름 배열. 수집기는 이 필드만 덮어쓰지 않는다(admin-v1.md "운영자 지정 필드")
object CuratedFields {
    const val NAME = "name"
    const val NAME_EN = "nameEn"
    const val DESCRIPTION = "description"
    const val DESCRIPTION_EN = "descriptionEn"
    const val ELIGIBILITY = "eligibility"

    fun read(value: JSONB?): Set<String> = FieldNames.read(value)
    fun jsonb(fields: Collection<String>): JSONB = FieldNames.jsonb(fields)
}

private object FieldNames {
    private val json = JsonMapper.builder().build()

    fun read(value: JSONB?): Set<String> =
        value?.data()?.let { runCatching { json.readValue<List<String>>(it) }.getOrNull() }?.toSet().orEmpty()

    fun jsonb(fields: Collection<String>): JSONB = JSONB.valueOf(json.writeValueAsString(fields.distinct()))
}
