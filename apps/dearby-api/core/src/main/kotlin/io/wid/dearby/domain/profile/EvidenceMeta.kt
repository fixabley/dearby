package io.wid.dearby.domain.profile

import java.time.OffsetDateTime
import java.util.*

data class EvidenceMeta(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime)
