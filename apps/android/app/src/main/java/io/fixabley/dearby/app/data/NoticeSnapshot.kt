package io.fixabley.dearby.app.data

import io.fixabley.dearby.entities.notice.model.NoticeModel
import io.fixabley.dearby.entities.organization.model.OrganizationModel

/** Transport composition only; never passed to rendering Views. */
internal data class NoticeSnapshot(val snapshotDate: String, val organizations: List<OrganizationModel>, val notices: List<NoticeModel>, val contentHash: String = "")
internal fun interface NoticeSnapshotReader { fun load(): NoticeSnapshot }
