package io.wid.dearby.admin.adaptor.geocode

import org.springframework.boot.context.properties.ConfigurationProperties

// dearby.geocode.kakao-key: 카카오 REST API 키(KAKAO_REST_API_KEY). 비어 있으면 Nominatim을 쓴다(admin-v1.md "활동 장소와 지오코딩")
@ConfigurationProperties("dearby.geocode")
data class GeocodeProperties(val kakaoKey: String = "")
