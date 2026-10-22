package io.wid.dearby.adaptor.geocode

import io.wid.dearby.application.geocode.GeocodeFailedException
import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode

// 카카오 로컬 API(키워드 검색 + 주소 검색). 키워드 결과를 먼저 두고 주소 문구가 같은 것은 하나만 남긴다. 최대 10개
class KakaoGeocoder(key: String, builder: RestClient.Builder) : Geocoder {
    private val http = builder.baseUrl(BASE_URL).defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK $key").build()

    override fun search(query: String): List<GeocodeResult> =
        (searchKeyword(query) + searchAddress(query)).distinctBy { it.address.ifEmpty { "${it.lat},${it.lng}" } }.take(LIMIT)

    override fun searchKeyword(query: String): List<GeocodeResult> = documents("/v2/local/search/keyword.json", query).map {
        GeocodeResult(
            name = it.text("place_name").ifEmpty { it.text("address_name") },
            address = it.text("road_address_name").ifEmpty { it.text("address_name") },
            lat = it.coordinate("y"), lng = it.coordinate("x"), source = SOURCE,
        )
    }

    override fun searchAddress(query: String): List<GeocodeResult> = documents("/v2/local/search/address.json", query).map {
        GeocodeResult(
            name = it.path("road_address").text("building_name").ifEmpty { it.text("address_name") },
            address = it.path("road_address").text("address_name").ifEmpty { it.text("address_name") },
            lat = it.coordinate("y"), lng = it.coordinate("x"), source = SOURCE,
        )
    }

    private fun documents(path: String, query: String): List<JsonNode> = try {
        http.get().uri { it.path(path).queryParam("query", query).queryParam("size", LIMIT).build() }
            .retrieve().body(JsonNode::class.java)?.path("documents")?.toList().orEmpty()
    } catch (e: RestClientException) {
        throw GeocodeFailedException("카카오 장소 검색에 실패했습니다: ${e.message}", e)
    }

    private fun JsonNode.text(field: String): String = path(field).asString("").trim()
    private fun JsonNode.coordinate(field: String): Double = text(field).toDoubleOrNull() ?: throw GeocodeFailedException("카카오 응답의 좌표를 읽을 수 없습니다")

    private companion object {
        const val BASE_URL = "https://dapi.kakao.com"
        const val SOURCE = "kakao"
        const val LIMIT = 10
    }
}
