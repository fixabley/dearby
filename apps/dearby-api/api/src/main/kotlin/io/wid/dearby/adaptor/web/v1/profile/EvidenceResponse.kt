package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.EvidenceMeta
import java.time.OffsetDateTime
import java.util.*

data class EvidenceResponse(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime) {
    companion object {
        fun from(meta: EvidenceMeta) = EvidenceResponse(meta.id, meta.fileName, meta.contentType, meta.size, meta.uploadedAt)
    }
}
