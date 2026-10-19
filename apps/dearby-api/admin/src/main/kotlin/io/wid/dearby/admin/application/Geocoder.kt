package io.wid.dearby.admin.application

// 어드민 장소 검색(admin-v1.md "활동 장소와 지오코딩"). 운영자가 고른 결과를 활동 장소 place로 저장한다
interface Geocoder {
    // 검색어로 장소 후보를 최대 10개. 실패하면 빈 목록이 아니라 예외를 던져 502로 알린다
    fun search(query: String): List<GeocodeResult>
}

// name: 장소 이름, address: 주소 문구, source: 제공자("kakao"·"nominatim")
data class GeocodeResult(val name: String, val address: String, val lat: Double, val lng: Double, val source: String)

class GeocodeFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
