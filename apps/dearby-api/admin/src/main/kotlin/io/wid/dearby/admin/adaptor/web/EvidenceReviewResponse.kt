package io.wid.dearby.admin.adaptor.web

import java.time.OffsetDateTime
import java.util.*

data class EvidenceReviewResponse(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime)
