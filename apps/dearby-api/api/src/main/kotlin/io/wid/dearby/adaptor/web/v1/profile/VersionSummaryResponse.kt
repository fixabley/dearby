package io.wid.dearby.adaptor.web.v1.profile

import java.time.OffsetDateTime

data class VersionSummaryResponse(val version: Int, val createdAt: OffsetDateTime)
