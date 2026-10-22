package io.wid.dearby.batch.application

import io.wid.dearby.application.geocode.GeocodeFailedException
import io.wid.dearby.application.geocode.Geocoder
import io.wid.dearby.domain.Address
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// 원문으로 확인한 장소(이름·주소)를 지오코딩한다(native-v1.md "수집기 장소"). 확실할 때만 좌표를 돌려주고 아니면 null(location 문구만 남는다).
// 확실함: 주소 검색 결과가 정확히 하나, 또는 키워드 검색 첫 결과의 이름이 제출한 이름을 포함하고(공백·대소문자 무시) 제출 주소가 있으면 그 시·군·구가 같을 때.
// 저장하는 이름은 제출한 이름, 주소·좌표는 제공자 값. 검색이 실패하면 장소 없이 둔다(수집은 막지 않는다)
@Service
class PlaceResolver(private val geocoder: Geocoder) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(query: PlaceQuery): Address? = try {
        val byAddress = query.address?.let(geocoder::searchAddress)?.singleOrNull()
        val match = byAddress ?: geocoder.searchKeyword(query.name).firstOrNull()?.takeIf { first ->
            compact(first.name).contains(compact(query.name)) &&
                (query.address == null || district(query.address)?.let { it == district(first.address) } == true)
        }
        match?.let { Address(lat = it.lat, lng = it.lng, addressLine1 = query.name, addressLine2 = it.address) }
    } catch (e: GeocodeFailedException) {
        log.warn("place geocode failed for '{}': {}", query.name, e.message)
        null
    }

    companion object {
        private val PROVINCE = Regex("^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)|(특별시|광역시|특별자치시|특별자치도|도)$")
        private val DISTRICT = Regex(".+(시|군|구)$")

        private fun compact(text: String) = text.replace(Regex("\\s"), "").lowercase()

        // 주소의 시·군·구(시·도 다음 첫 "…시·군·구" 낱말). 읽을 수 없으면 null이고 같지 않은 것으로 본다
        fun district(address: String): String? {
            val words = address.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val rest = if (words.firstOrNull()?.let { PROVINCE.containsMatchIn(it) } == true) words.drop(1) else words
            return rest.firstOrNull { DISTRICT.matches(it) }
        }
    }
}

// 원문으로 확인한 장소 검색어
data class PlaceQuery(val name: String, val address: String?)
