package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.recommendation.RecommendationRules
import io.wid.dearby.domain.Tag
import io.wid.dearby.domain.TagKind
import java.security.MessageDigest

// 임베딩 글(native-v1.md "활동 벡터"): 프로그램·조직·활동 이름·종류·분야·성격 태그·hook·highlights·대상 원문·지역(시·도)·설명 전체.
// 날짜·시각·마감·비용 숫자·나이 조건·URL·연락처는 빼고(규칙이 처리한다) 설명은 맨 뒤에 둬 모델 최대 길이에서 잘리면 설명 끝이 잘린다
object EmbeddingText {
    private val REMOVE = listOf(
        Regex("""https?://\S+|www\.\S+"""), // URL
        Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""), // 이메일
        Regex("""\(?0\d{1,2}\)?[-. ]?\d{3,4}[-. ]?\d{4}|\b1[5-9]\d{2}-\d{4}\b"""), // 전화
        Regex("""\d{2,4}\s*[./-]\s*\d{1,2}\s*[./-]\s*\d{1,2}\.?"""), // 2026.11.01
        Regex("""(\d{2,4}\s*년\s*)?\d{1,2}\s*월(\s*\d{1,2}\s*일)?(\s*\([월화수목금토일]\))?"""), // 11월 1일(토)
        Regex("""\d{1,2}\s*:\s*\d{2}"""), // 시각
        Regex("""\d[\d,]*\s*(만\s*)?원"""), // 금액
        Regex("""만?\s*\d{1,3}\s*세(\s*(이상|이하|미만|초과))?"""), // 나이 조건
    )
    private val REGIONS = listOf(
        "서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천", "광주" to "광주", "대전" to "대전", "울산" to "울산", "세종" to "세종",
        "경기" to "경기", "강원" to "강원", "충청북" to "충북", "충북" to "충북", "충청남" to "충남", "충남" to "충남", "전라북" to "전북", "전북" to "전북",
        "전라남" to "전남", "전남" to "전남", "경상북" to "경북", "경북" to "경북", "경상남" to "경남", "경남" to "경남", "제주" to "제주",
    )

    fun of(listing: ActivityListing): String {
        val activity = listing.activity
        val tags = (activity.fieldTags + activity.tags).distinctBy { it.id }
        fun names(kind: TagKind) = tags.filter { it.kind == kind }.joinToString(", ", transform = Tag::name)
        return listOfNotNull(
            "프로그램: ${listing.program.name}",
            "조직: ${listing.organization.name}",
            "활동: ${activity.name}",
            activity.category?.let { "종류: ${RecommendationRules.CATEGORY_NAMES.getValue(it)}" },
            names(TagKind.FIELD).ifEmpty { null }?.let { "분야: $it" },
            names(TagKind.TRAIT).ifEmpty { null }?.let { "얻을 수 있는 것: $it" },
            activity.hook?.let { "소개: ${clean(it)}" },
            activity.highlights.takeIf { it.isNotEmpty() }?.let { "핵심: ${it.joinToString(" / ") { h -> clean(h.text) }}" },
            activity.audience?.let(::clean)?.ifEmpty { null }?.let { "대상: $it" },
            region(activity.activitySchedule.address?.addressLine2)?.let { "지역: $it" }, // 구조화된 장소 주소에서만(자유 문구 location은 쓰지 않는다)
            clean(activity.description).ifEmpty { null }?.let { "설명: $it" },
        ).joinToString("\n")
    }

    fun of(tag: Tag): String = listOfNotNull(tag.name, tag.nameEn).joinToString(" / ")

    // 글이 바뀐 것만 다시 임베딩한다: sha256(모델 + 글)
    fun hash(model: String, text: String): String =
        MessageDigest.getInstance("SHA-256").digest("$model\n$text".toByteArray()).joinToString("") { "%02x".format(it) }

    fun clean(text: String): String = REMOVE.fold(text) { acc, pattern -> pattern.replace(acc, " ") }.replace(Regex("""[ \t]+"""), " ").replace(Regex("""\s*\n\s*"""), "\n").trim()

    private fun region(text: String?): String? = text?.trim()?.let { t -> REGIONS.firstOrNull { t.startsWith(it.first) }?.second }
}
