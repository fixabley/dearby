package io.fixabley.dearby.features.addtocalendar.model

import java.net.URI

internal fun calendarWebUrl(value: String?): String? = value?.takeIf {
    runCatching {
        val uri = URI(it)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
