package io.wid.dearby.application.geocode

// 장소 검색(admin-v1.md "활동 장소와 지오코딩", native-v1.md "수집기 장소"). 어드민 장소 검색과 수집기가 같은 제공자를 쓴다
interface Geocoder {
    // 검색어로 장소 후보를 최대 10개(키워드 + 주소). 실패하면 빈 목록이 아니라 예외를 던진다(어드민은 502)
    fun search(query: String): List<GeocodeResult>

    // 주소 검색만. 주소 검색이 따로 없는 제공자(Nominatim)는 search와 같다
    fun searchAddress(query: String): List<GeocodeResult> = search(query)

    // 장소 이름 키워드 검색만. 따로 없는 제공자는 search와 같다
    fun searchKeyword(query: String): List<GeocodeResult> = search(query)
}

// name: 장소 이름, address: 주소 문구, source: 제공자("kakao"·"nominatim")
data class GeocodeResult(val name: String, val address: String, val lat: Double, val lng: Double, val source: String)

class GeocodeFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
