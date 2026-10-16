package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.Profile
import java.time.OffsetDateTime
import java.util.*

// id는 사용자 id. version은 If-Match에 쓴다
data class ProfileResponse(
    val id: UUID,
    val name: String,
    val job: String,
    val introduction: String,
    val contacts: List<ContactResponse>,
    val histories: List<HistoryResponse>,
    val version: Int,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(p: Profile) = ProfileResponse(
            p.userId, p.name, p.job, p.introduction,
            p.contacts.map(ContactResponse::from), p.histories.map(HistoryResponse::from), p.version, p.updatedAt,
        )
    }
}
