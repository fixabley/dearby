package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.ProfileVersion
import java.time.OffsetDateTime

data class VersionResponse(val version: Int, val createdAt: OffsetDateTime, val profile: ProfileResponse) {
    companion object {
        fun from(v: ProfileVersion) = VersionResponse(v.version, v.createdAt, ProfileResponse.from(v.profile))
    }
}
