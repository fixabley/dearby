package io.wid.dearby.admin.adaptor.geocode

import io.wid.dearby.admin.application.Geocoder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

// 제공자 선택: 카카오 키가 있으면 카카오(키워드+주소), 없으면 Nominatim(OpenStreetMap). 만들 때 네트워크를 쓰지 않는다
@Configuration
class GeocodeConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun geocoder(properties: GeocodeProperties): Geocoder {
        val key = properties.kakaoKey.trim()
        return if (key.isNotEmpty()) KakaoGeocoder(key, RestClient.builder())
        else NominatimGeocoder(RestClient.builder()).also { log.info("KAKAO_REST_API_KEY not set; admin geocode uses Nominatim") }
    }
}
