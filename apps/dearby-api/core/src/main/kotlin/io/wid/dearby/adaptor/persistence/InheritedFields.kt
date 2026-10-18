package io.wid.dearby.adaptor.persistence

import org.jooq.JSONB
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// activities.inherited_fields: 이전 기수에서 가져온 필드 이름 배열(admin-v1.md "이전 기수 정보 자동완성")
object InheritedFields {
    const val ELIGIBILITY = "eligibility"
    const val AUDIENCE = "audience"
    const val PARTICIPATION_TYPE = "participationType"

    private val json = JsonMapper.builder().build()

    fun read(value: JSONB?): Set<String> =
        value?.data()?.let { runCatching { json.readValue<List<String>>(it) }.getOrNull() }?.toSet().orEmpty()

    fun jsonb(fields: Collection<String>): JSONB = JSONB.valueOf(json.writeValueAsString(fields.toList()))
}
