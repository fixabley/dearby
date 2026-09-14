package io.fixabley.dearby.entities.activitycatalog.model

internal data class ActivitySource(val id: String, val url: String?, val kind: String?, val checkedAt: String?, val access: String?, val note: String?)
internal data class ActivityEvidence(val sourceId: String, val locator: String, val fieldPath: String, val sourceURL: String?)
