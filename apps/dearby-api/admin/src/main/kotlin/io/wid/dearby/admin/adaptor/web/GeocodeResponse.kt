package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.geocode.GeocodeResult

// GET /v1/admin/geocode 응답(admin-v1.md "활동 장소와 지오코딩"). source는 제공자("kakao"·"nominatim")
data class GeocodeResponse(val items: List<GeocodeItemResponse>)

data class GeocodeItemResponse(val name: String, val address: String, val lat: Double, val lng: Double, val source: String) {
    companion object {
        fun from(result: GeocodeResult) = GeocodeItemResponse(result.name, result.address, result.lat, result.lng, result.source)
    }
}
