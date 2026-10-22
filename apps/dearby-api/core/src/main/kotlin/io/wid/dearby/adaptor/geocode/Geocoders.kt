package io.wid.dearby.adaptor.geocode

import io.wid.dearby.application.geocode.Geocoder
import org.springframework.web.client.RestClient

// 제공자 선택: 카카오 키가 있으면 카카오(키워드+주소), 없으면 Nominatim(OpenStreetMap). 만들 때 네트워크를 쓰지 않는다.
// 빈으로 등록하지 않는다(api 앱은 쓰지 않음). 어드민·batch가 각자 설정에서 부른다
object Geocoders {
    fun create(kakaoKey: String): Geocoder {
        val key = kakaoKey.trim()
        return if (key.isNotEmpty()) KakaoGeocoder(key, RestClient.builder()) else NominatimGeocoder(RestClient.builder())
    }
}
