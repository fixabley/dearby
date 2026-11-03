package io.wid.dearby.domain

// 장소 짧은 꼴(native-v1.md "행 컴포넌트" placeShort, 2026-11-03 "서울 유성구처럼 잘라서"): 행의 핀 줄에 들어가는 `시·도 짧은 이름 + 구·군`.
// 지오코딩 응답의 행정구역 이름은 저장하지 않으므로(Address는 addressLine1·addressLine2뿐) 주소 문구를 앞에서부터 읽는다:
// 카카오 도로명 주소 "서울 강남구 영동대로 513"·지번 "서울특별시 강남구 …"·"경기도 성남시 분당구 …" 모두 시·도(긴 이름·짧은 이름) → 시·군·구 순이다.
// 주소가 없으면 location 문구에서 같은 규칙으로 지역 이름을 뽑는다(못 뽑으면 null → 줄 없음).
// 여러 곳이면 첫 곳 + `외 n곳`, 온라인이면 `온라인`, 온·오프라인이 섞이면 `온라인·서울`/`온라인·해외`
object PlaceShort {

    // 한 활동의 장소 재료: 좌표 장소의 주소(addressLine2)와 장소 문구(activities.location)
    data class Source(val address: String?, val location: String?)

    // 시·도 긴 이름 → 짧은 이름. 짧은 이름 자체도 문구에서 찾는다(카카오 도로명 주소는 짧은 이름으로 온다)
    private val SIDO: Map<String, String> = linkedMapOf(
        "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천", "광주광역시" to "광주", "대전광역시" to "대전",
        "울산광역시" to "울산", "세종특별자치시" to "세종", "경기도" to "경기", "강원특별자치도" to "강원", "강원도" to "강원",
        "충청북도" to "충북", "충청남도" to "충남", "전북특별자치도" to "전북", "전라북도" to "전북", "전라남도" to "전남",
        "경상북도" to "경북", "경상남도" to "경남", "제주특별자치도" to "제주", "제주도" to "제주",
        "서울시" to "서울", "부산시" to "부산", "대구시" to "대구", "인천시" to "인천", "대전시" to "대전", "울산시" to "울산", "세종시" to "세종",
        "서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천", "광주" to "광주", "대전" to "대전", "울산" to "울산", "세종" to "세종",
        "경기" to "경기", "강원" to "강원", "충북" to "충북", "충남" to "충남", "전북" to "전북", "전남" to "전남", "경북" to "경북", "경남" to "경남", "제주" to "제주",
    )
    private val SIDO_PATTERN = Regex("(?<![가-힣])(" + SIDO.keys.sortedByDescending { it.length }.joinToString("|") + ")(?![가-힣])")
    private val DISTRICT = Regex("^[가-힣]{1,5}[시군구]$") // 시·군·구 한 토큰
    private val SEPARATORS = Regex("[\\s,·/()\\[\\]]+")
    private val ONLINE = Regex("온라인|비대면|online|zoom|웨비나|webinar|메타버스|유튜브|youtube|discord|디스코드", RegexOption.IGNORE_CASE)
    private val ABROAD = Regex("해외|미국|일본|중국|싱가포르|베트남|독일|영국|프랑스|캐나다|호주|대만|홍콩|태국|인도네시아|인도|유럽|실리콘밸리|샌프란시스코|뉴욕|도쿄|상하이|베를린|런던|파리|싱가폴", RegexOption.IGNORE_CASE)

    const val ONLINE_LABEL = "온라인"
    const val ABROAD_LABEL = "해외"

    private data class Region(val sido: String, val district: String?)
    private class Parsed(val regions: List<Region>, val online: Boolean, val abroad: Boolean) {
        val isEmpty get() = regions.isEmpty() && !online && !abroad
    }

    fun of(address: String?, location: String?): String? = ofAll(listOf(Source(address, location)))

    // 여러 활동(프로그램 묶음)의 장소를 합친다. 활동마다 주소가 있으면 주소만, 없으면 문구
    fun ofAll(sources: Collection<Source>): String? {
        val parsed = sources.map { source ->
            source.address?.takeIf { it.isNotBlank() }?.let(::parse)?.takeUnless { it.isEmpty } ?: parse(source.location)
        }
        val regions = parsed.flatMap { it.regions }
        val sidos = regions.map { it.sido }.distinct()
        val online = parsed.any { it.online }
        val abroad = parsed.any { it.abroad }
        val places = sidos + if (abroad) listOf(ABROAD_LABEL) else emptyList()
        return when {
            places.isEmpty() -> if (online) ONLINE_LABEL else null
            online -> "$ONLINE_LABEL·${places.first()}"
            places.size > 1 -> "${places.first()} 외 ${places.size - 1}곳"
            abroad -> ABROAD_LABEL
            else -> {
                val districts = regions.filter { it.sido == sidos.single() }.mapNotNull { it.district }.distinct()
                if (districts.size == 1) "${sidos.single()} ${districts.single()}" else sidos.single()
            }
        }
    }

    private fun parse(text: String?): Parsed {
        if (text.isNullOrBlank()) return Parsed(emptyList(), online = false, abroad = false)
        val regions = SIDO_PATTERN.findAll(text).map { match ->
            val sido = SIDO.getValue(match.groupValues[1])
            Region(sido, district(sido, text.substring(match.range.last + 1)))
        }.distinctBy { it.sido }.toList()
        return Parsed(regions, online = ONLINE.containsMatchIn(text), abroad = ABROAD.containsMatchIn(text))
    }

    // 시·도 바로 뒤의 시·군·구: "강남구" → 강남구, "성남시 분당구" → 분당구를 앞세운 "성남 분당구", "파주시" → 파주시, "세종" 아래는 없음
    private fun district(sido: String, rest: String): String? {
        val tokens = rest.trimStart().split(SEPARATORS).filter { it.isNotEmpty() }.takeWhile { DISTRICT.matches(it) }.take(2)
        val first = tokens.getOrNull(0) ?: return null
        val second = tokens.getOrNull(1)
        return when {
            first.endsWith("시") && second != null && second.endsWith("구") -> "${first.dropLast(1)} $second"
            else -> first
        }
    }
}
