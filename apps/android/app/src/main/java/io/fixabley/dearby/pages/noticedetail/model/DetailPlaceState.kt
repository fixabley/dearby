package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.entities.notice.model.NoticeVenue

internal data class DetailPlaceState(val name: String, val detail: String?, val description: String)

/** Split only unambiguous trailing floor/room markers; never shorten a street address. */
internal fun detailPlace(venue: NoticeVenue): DetailPlaceState {
    val full = venue.displayName
    val floor = Regex("\\(([^()]*\\d+층[^()]*)\\)$").find(full)
    val room = Regex("\\s+(\\d+호(?:실)?(?:[·,]\\d+호(?:실)?)*)$").find(full)
    val suffix = floor ?: room
    val name = suffix?.let { full.substring(0, it.range.first).trim() }?.takeIf { it.isNotEmpty() } ?: full
    val details = listOfNotNull(suffix?.groupValues?.get(1), venue.address?.takeUnless { it == full }).distinct()
    return DetailPlaceState(name, details.joinToString("\n").takeIf { it.isNotEmpty() },
        listOfNotNull(venue.name, venue.address).distinct().joinToString(" · ").ifBlank { full })
}

/** Remove a summary only when its complete text is already contained in a displayed venue. */
internal fun locationNote(summary: String, displayed: List<NoticeVenue>): String? {
    fun key(s: String) = s.filterNot { it.isWhitespace() || it in "()" }
    val value = key(summary)
    return summary.takeUnless { value.isNotEmpty() && displayed.any { venue ->
        listOfNotNull(venue.name, venue.address).any { key(it).contains(value) }
    } }
}
