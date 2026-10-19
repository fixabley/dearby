package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.Address

// 공개 API의 PlaceResponse와 같은 형태(admin-v1.md "활동 장소와 지오코딩")
data class PlaceResponse(val name: String, val address: String, val lat: Double, val lng: Double) {
    companion object {
        fun from(address: Address) = PlaceResponse(address.addressLine1, address.addressLine2, address.lat, address.lng)
    }
}
