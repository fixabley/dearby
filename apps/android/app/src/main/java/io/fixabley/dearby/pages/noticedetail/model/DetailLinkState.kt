package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.features.addtocalendar.model.calendarWebUrl
import java.net.URI

internal data class DetailLinkState(val url: String, val domain: String)
internal fun detailLink(url: String?): DetailLinkState? = calendarWebUrl(url)?.let {
    DetailLinkState(it, URI(it).host)
}
