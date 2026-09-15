package io.fixabley.dearby.pages.noticedetail.model

import java.net.URI

internal data class DetailLinkState(val url: String, val domain: String)
/** Same HTTP(S)/host/no-userinfo policy as Calendar; no dependency on that feature internals. */
internal fun detailLink(url: String?): DetailLinkState? = url?.let {
    runCatching {
        val uri = URI(it)
        if (uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
            DetailLinkState(it, uri.host) else null
    }.getOrNull()
}
