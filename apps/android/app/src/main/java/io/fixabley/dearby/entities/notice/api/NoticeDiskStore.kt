package io.fixabley.dearby.entities.notice.api

import io.fixabley.dearby.entities.notice.model.NoticeModel

internal interface NoticeDiskStore {
    fun find(id: String): NoticeModel?
    fun upsert(value: NoticeModel)
}

/** Worker-thread L2→mock L3 source; errors propagate before the enclosing repository caches. */
internal class StoredNoticeSource(private val disk: NoticeDiskStore, private val external: NoticeSource) : NoticeSource {
    override fun find(id: String): NoticeModel? = disk.find(id)?.also { require(it.id == id) { "Stored ID mismatch" } }
        ?: external.find(id)?.also { require(it.id == id) { "External ID mismatch" }; disk.upsert(it) }
}
