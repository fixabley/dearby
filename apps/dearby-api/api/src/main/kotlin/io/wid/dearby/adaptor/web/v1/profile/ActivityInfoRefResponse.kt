package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.ActivityInfoRef

data class ActivityInfoRefResponse(val id: Long, val name: String) {
    companion object {
        fun from(ref: ActivityInfoRef) = ActivityInfoRefResponse(ref.id, ref.name)
    }
}
