package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.DictionaryTag
import com.dearby.nativeapp.entities.account.model.EligibilityProfile
import com.dearby.nativeapp.entities.account.model.RecommendationPreferences
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.features.analytics.Analytics

/**
 * The parameters and user properties of `shared/contracts/analytics-v1.md` (2026-10-25): ids, the upper field's `nameEn` and the category code only —
 * never 지원 자격 values, names or a user id. [learn] takes the public tag dictionary so a field tag can be named by its upper field.
 */
object Tracking {
    private var dictionary: List<DictionaryTag> = emptyList()
    private var upper: Map<Long, String> = emptyMap()

    fun learn(tags: List<DictionaryTag>) {
        dictionary = tags
        val byId = tags.associateBy { it.id }
        upper = tags.filter { it.kind == "FIELD" }.associate { tag -> tag.id to ((byId[tag.parentId] ?: tag).let { it.nameEn ?: it.name }).lowercase() }
    }

    /** `field` (the first field tag's upper field) and `category` (lower-case code) of an activity, with its `activity_id` unless [idKey] is null. */
    fun activity(model: ActivityModel?, id: String, idKey: String? = "activity_id"): Array<Pair<String, String>> = listOfNotNull(
        idKey?.let { it to id },
        model?.fieldTags?.firstNotNullOfOrNull { upper[it.id.toLongOrNull()] ?: it.nameEn?.lowercase() }?.let { "field" to it },
        model?.category?.let { "category" to it.name.lowercase() },
    ).toTypedArray()

    /** `interest_fields`, `interest_kinds`, `has_eligibility`; null [preferences] clears them (signed out). */
    fun interests(preferences: RecommendationPreferences?, eligibility: EligibilityProfile?) {
        Analytics.property("interest_fields", preferences?.fieldTagIds?.mapNotNull { upper[it] }?.distinct()?.joinToString(",")?.take(36))
        Analytics.property("interest_kinds", preferences?.categories?.joinToString(",") { it.lowercase() }?.take(36))
        Analytics.property("has_eligibility", preferences?.let { if (eligibility?.summary() != null) "yes" else "no" })
    }

    fun signedIn(on: Boolean) = Analytics.property("signed_in", if (on) "yes" else "no")
    fun menu(name: String) = Analytics.event("menu_open", "menu" to name)
}
