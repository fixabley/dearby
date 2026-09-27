package com.dearby.nativeapp.features.application

import java.net.URI

fun safeWebUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null && value.none { it.isISOControl() }
}.getOrDefault(false)

