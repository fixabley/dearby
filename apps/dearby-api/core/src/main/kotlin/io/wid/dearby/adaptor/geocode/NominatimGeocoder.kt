package io.wid.dearby.adaptor.geocode

import io.wid.dearby.application.geocode.GeocodeFailedException
import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode

// OpenStreetMap Nominatim(카카오 키가 없을 때). 이용 정책에 따라 식별 가능한 User-Agent를 보내고 초당 1회로 제한한다
class NominatimGeocoder(builder: RestClient.Builder) : Geocoder {
    private val http = builder.baseUrl(BASE_URL).defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build()
    private val lock = Any()
    private var lastRequestAt = 0L

    override fun search(query: String): List<GeocodeResult> {
        throttle()
        val items = try {
            http.get().uri { it.path("/search").queryParam("q", query).queryParam("format", "jsonv2").queryParam("limit", LIMIT).queryParam("accept-language", "ko").build() }
                .retrieve().body(JsonNode::class.java)?.toList().orEmpty()
        } catch (e: RestClientException) {
            throw GeocodeFailedException("Nominatim 장소 검색에 실패했습니다: ${e.message}", e)
        }
        return items.map {
            val display = it.path("display_name").asString("").trim()
            GeocodeResult(
                name = it.path("name").asString("").trim().ifEmpty { display.substringBefore(",").trim() },
                address = display,
                lat = it.path("lat").asString("").toDoubleOrNull() ?: throw GeocodeFailedException("Nominatim 응답의 좌표를 읽을 수 없습니다"),
                lng = it.path("lon").asString("").toDoubleOrNull() ?: throw GeocodeFailedException("Nominatim 응답의 좌표를 읽을 수 없습니다"),
                source = SOURCE,
            )
        }.distinctBy { it.address }.take(LIMIT)
    }

    // 직전 요청에서 1초가 지나지 않았으면 기다린다(Nominatim 이용 정책: 초당 1회)
    private fun throttle() = synchronized(lock) {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastRequestAt = System.currentTimeMillis()
    }

    private companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org"
        const val USER_AGENT = "Dearby/1.0 (contact: fixabley@naver.com)"
        const val SOURCE = "nominatim"
        const val LIMIT = 10
        const val MIN_INTERVAL_MS = 1_000L
    }
}
