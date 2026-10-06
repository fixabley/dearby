package com.dearby.nativeapp.shared.config

import java.net.URI
import java.util.UUID

/** The link a share's QR carries: `<web origin>/s/<share ID>`. */
fun sharedCardUrl(shareId: String, webOrigin: String) = "$webOrigin/s/$shareId"

/** Exactly `<web origin>/s/<UUID>` → lower-case share ID; query, fragment or anything else is ignored. */
fun sharedCardId(link: String, webOrigin: String): String? = runCatching {
    val uri = URI(link)
    val web = URI(webOrigin)
    if (uri.scheme != web.scheme || uri.host?.lowercase() != web.host || uri.port != web.port) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null
    val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    if (path.size != 2 || path[0] != "s") return null
    // UUID.fromString accepts short groups, so require the canonical form back.
    UUID.fromString(path[1]).toString().takeIf { it.equals(path[1], ignoreCase = true) }
}.getOrNull()
