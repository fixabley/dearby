package io.wid.dearby.batch.adaptor.geocode

import io.wid.dearby.adaptor.geocode.Geocoders
import io.wid.dearby.application.geocode.Geocoder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 수집기 장소 지오코딩(native-v1.md "수집기 장소"): 어드민 장소 검색과 같은 제공자. 카카오 키가 없으면 Nominatim
@Configuration
class GeocodeConfig {
    @Bean
    fun geocoder(properties: GeocodeProperties): Geocoder = Geocoders.create(properties.kakaoKey)
}

// dearby.geocode.kakao-key: KAKAO_REST_API_KEY(비면 Nominatim)
@ConfigurationProperties("dearby.geocode")
data class GeocodeProperties(val kakaoKey: String = "")
