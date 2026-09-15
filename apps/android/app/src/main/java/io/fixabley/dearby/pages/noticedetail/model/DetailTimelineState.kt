package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.shared.ui.TimelineInterval
import io.fixabley.dearby.shared.ui.compactPeriodText
import java.time.OffsetDateTime
import java.time.ZoneId

/** No bars for deadline-only, date-only, mixed precision, invalid or conflicting sources. */
internal fun detailTimeline(startAt: String?, startOn: String?, endAt: String?, endOn: String?, timezone: String): TimelineInterval? {
    if (startAt == null || endAt == null) return null
    if (compactPeriodText(startAt, startOn, endAt, endOn, timezone, "") == "") return null
    return runCatching { TimelineInterval(OffsetDateTime.parse(startAt).toInstant(),
        OffsetDateTime.parse(endAt).toInstant(), ZoneId.of(timezone)) }.getOrNull()
}
