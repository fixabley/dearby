package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.domain.Address

// 활동 장소(native-v1.md "활동 장소"): 활동 기간 일정의 address(addressLine1 = 이름, addressLine2 = 주소, 좌표)
data class PlaceResponse(val name: String, val address: String, val lat: Double, val lng: Double) {
    companion object {
        fun from(address: Address) = PlaceResponse(address.addressLine1, address.addressLine2, address.lat, address.lng)
    }
}
