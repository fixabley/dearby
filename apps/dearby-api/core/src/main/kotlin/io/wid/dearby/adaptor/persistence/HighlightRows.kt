package io.wid.dearby.adaptor.persistence

import io.wid.dearby.domain.Highlight
import io.wid.dearby.domain.HighlightIcon
import org.jooq.JSONB
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

// activities.highlights: [{icon, text}]. 모르는 icon은 읽을 때 버린다
object HighlightRows {
    private val json = JsonMapper.builder().build()

    fun read(value: JSONB?): List<Highlight> =
        value?.data()?.let { runCatching { json.readValue<List<Map<String, String?>>>(it) }.getOrNull() }.orEmpty()
            .mapNotNull { row -> HighlightIcon.of(row["icon"])?.let { icon -> row["text"]?.let { Highlight(icon, it) } } }

    fun jsonb(items: List<Highlight>): JSONB? =
        items.takeIf { it.isNotEmpty() }?.let { JSONB.valueOf(json.writeValueAsString(it.map { h -> mapOf("icon" to h.icon.code, "text" to h.text) })) }
}
