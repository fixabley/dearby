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
    const val OFFICIAL_URL = "officialUrl" // 2026-10-19 어드민에서 공식 안내·신청 링크도 고친다
    const val APPLICATION_URL = "applicationUrl"
    const val AUDIENCE = InheritedFields.AUDIENCE // 운영자 직접 등록(2026-10-19)이 남기는 이름들
    const val PARTICIPATION_TYPE = InheritedFields.PARTICIPATION_TYPE
    const val LOCATION = "location"
    const val COST = "cost"
    const val CATEGORY = "category" // 활동 종류(native-v1.md "활동 종류", 2026-10-19)
    const val TAGS = "tags" // 태그(native-v1.md "태그", 2026-10-19). activity_tags 연결을 운영자가 정했음

    fun read(value: JSONB?): Set<String> = FieldNames.read(value)
    fun jsonb(fields: Collection<String>): JSONB = FieldNames.jsonb(fields)
}

private object FieldNames {
    private val json = JsonMapper.builder().build()

    fun read(value: JSONB?): Set<String> =
        value?.data()?.let { runCatching { json.readValue<List<String>>(it) }.getOrNull() }?.toSet().orEmpty()

    fun jsonb(fields: Collection<String>): JSONB = JSONB.valueOf(json.writeValueAsString(fields.distinct()))
}
