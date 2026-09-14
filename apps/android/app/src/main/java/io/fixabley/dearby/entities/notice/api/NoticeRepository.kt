package io.fixabley.dearby.entities.notice.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import io.fixabley.dearby.entities.notice.model.NoticeModel

/** Independent lazy cache; App replaces sources on its UI-thread snapshot boundary. */
internal class NoticeRepository(private var source: NoticeSource) {
    private val cache = mutableMapOf<String, NoticeModel>()
    var revision by mutableIntStateOf(0)
        private set
    @Synchronized fun find(id: String?): NoticeModel? {
        if (id == null) return null
        return cache[id] ?: source.find(id)?.also { cache[id] = it }
    }
    @Synchronized fun replaceSource(replacement: NoticeSource) {
        source = replacement
        cache.clear()
        revision++
    }
}
