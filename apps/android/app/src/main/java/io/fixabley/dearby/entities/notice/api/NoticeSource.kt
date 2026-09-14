package io.fixabley.dearby.entities.notice.api

import io.fixabley.dearby.entities.notice.model.NoticeModel

internal fun interface NoticeSource { fun find(id: String): NoticeModel? }
internal class InMemoryNoticeSource(records: List<NoticeModel>) : NoticeSource {
    private val records = records.associateBy { it.id }
    override fun find(id: String) = records[id]
}
