package com.dearby.nativeapp.entities.account.model

import com.dearby.nativeapp.shared.config.sharedCardId
import java.net.URI
import java.util.UUID

/** What a scanned QR or opened link points at: a share (`<web>/s/<UUID>`) or, from older codes, a card (`dearby://card/<UUID>`). */
sealed interface ScannedLink {
    data class Share(val id: String) : ScannedLink
    data class Card(val id: String) : ScannedLink
    companion object {
        fun parse(text: String, webOrigin: String): ScannedLink? {
            val trimmed = text.trim()
            sharedCardId(trimmed, webOrigin)?.let { return Share(it) }
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (uri.scheme != "dearby" || uri.host != "card" || uri.rawQuery != null || uri.rawFragment != null) return null
            val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val id = path.singleOrNull()?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() } ?: return null
            return if (id.equals(path[0], ignoreCase = true)) Card(id) else null
        }
    }
}
