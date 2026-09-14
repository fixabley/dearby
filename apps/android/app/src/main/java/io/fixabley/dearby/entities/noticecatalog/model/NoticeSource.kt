package io.fixabley.dearby.entities.noticecatalog.model

internal data class NoticeSource(val id: String, val url: String?, val kind: String?, val checkedAt: String?, val access: String?, val note: String?)
internal data class NoticeEvidence(val sourceId: String, val locator: String, val fieldPath: String, val sourceURL: String?)
