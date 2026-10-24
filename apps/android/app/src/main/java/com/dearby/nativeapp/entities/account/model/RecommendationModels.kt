package com.dearby.nativeapp.entities.account.model

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/**
 * 추천 설정 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24): the funnel's answers. [completedAt] is set once the funnel was finished,
 * skipped to the end or put off (`나중에`); null means it has not been shown yet. The same JSON is the server body and the device copy.
 */
data class RecommendationPreferences(val fieldTagIds: List<Long> = emptyList(), val categories: List<String> = emptyList(), val traitTagIds: List<Long> = emptyList(), val completedAt: String? = null) {
    val empty get() = fieldTagIds.isEmpty() && categories.isEmpty() && traitTagIds.isEmpty()
    fun toJson(): JSONObject = JSONObject().put("fieldTagIds", JSONArray(fieldTagIds)).put("categories", JSONArray(categories))
        .put("traitTagIds", JSONArray(traitTagIds)).put("completedAt", completedAt ?: JSONObject.NULL)
}
fun JSONObject.toRecommendationPreferences() = RecommendationPreferences(longs("fieldTagIds"), strings("categories"), longs("traitTagIds"), optText("completedAt"))

/** 지원 자격 정보: [consentVersion] is the agreed version (null = no consent); values are the contract's English vocabulary. Never logged. */
data class EligibilityProfile(val consentVersion: String?, val educationStatus: String?, val employment: String?, val schoolYear: Int?, val birthYear: Int?)
fun JSONObject.toEligibilityProfile() = EligibilityProfile(optJSONObject("consent")?.optText("version"), optText("educationStatus"), optText("employment"),
    if (isNull("schoolYear") || !has("schoolYear")) null else getInt("schoolYear"), if (isNull("birthYear") || !has("birthYear")) null else getInt("birthYear"))
fun EligibilityProfile.toPutJson(): JSONObject = JSONObject().put("consentVersion", consentVersion).apply {
    educationStatus?.let { put("educationStatus", it) }; employment?.let { put("employment", it) }
    schoolYear?.let { put("schoolYear", it) }; birthYear?.let { put("birthYear", it) }
}

/** One recommended activity: only what the 추천 row shows; the detail is read by id when it opens. [reasons] are the server's short phrases. */
data class Recommendation(val activityId: String, val name: String, val program: String, val organization: String, val logoUrl: String?, val reasons: List<String>)
/** One answer of the recommendation API: [total] is the server's count of matches (`total`, when it sends one), else the items' count. */
data class RecommendationPage(val items: List<Recommendation>, val total: Int)
/** `{items: [{activity, program, reasons}], total?}`; an unreadable item is skipped. The program logo path is resolved against [api]. */
fun JSONObject.toRecommendations(api: String): RecommendationPage = recommendationItems(api).let { RecommendationPage(it, if (has("total") && !isNull("total")) getInt("total") else it.size) }
private fun JSONObject.recommendationItems(api: String): List<Recommendation> = getJSONArray("items").let { items -> List(items.length()) { items.getJSONObject(it) } }.mapNotNull { item ->
    runCatching {
        val activity = item.getJSONObject("activity")
        val program = item.optJSONObject("program") ?: activity.getJSONObject("program")
        Recommendation(activity.getLong("id").toString(), activity.getString("name"), program.getString("name"),
            activity.getJSONObject("organization").getString("name"),
            program.optText("logoUrl")?.let { path -> runCatching { URI(api).resolve(path).toString() }.getOrNull() }, item.strings("reasons"))
    }.getOrNull()
}

/** A dictionary tag with its kind, icon key [nameEn] (`FIELD`·`TRAIT`) and upper field (contract "태그 종류"·"하위 분야"); older APIs without `kind` read as TRAIT. */
data class DictionaryTag(val id: Long, val name: String, val kind: String, val parentId: Long?, val nameEn: String? = null)
fun JSONObject.toDictionaryTags(): List<DictionaryTag> = getJSONArray("items").let { items -> List(items.length()) { items.getJSONObject(it) } }.mapNotNull { tag ->
    runCatching { DictionaryTag(tag.getLong("id"), tag.getString("name"), tag.optText("kind") ?: "TRAIT", if (tag.isNull("parentId") || !tag.has("parentId")) null else tag.getLong("parentId"), tag.optText("nameEn")) }.getOrNull()
}

private fun JSONObject.optText(key: String): String? = if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
private fun JSONObject.longs(key: String): List<Long> = optJSONArray(key)?.let { array -> List(array.length()) { array.getLong(it) } } ?: emptyList()
private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList()
