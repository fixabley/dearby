package io.wid.dearby.admin.adaptor.geocode

import io.wid.dearby.adaptor.geocode.Geocoders
import io.wid.dearby.application.geocode.Geocoder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 어드민 장소 검색의 제공자(core Geocoders). 카카오 키가 없으면 Nominatim
@Configuration
class GeocodeConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun geocoder(properties: GeocodeProperties): Geocoder {
        if (properties.kakaoKey.isBlank()) log.info("KAKAO_REST_API_KEY not set; admin geocode uses Nominatim")
        return Geocoders.create(properties.kakaoKey)
    }
}
