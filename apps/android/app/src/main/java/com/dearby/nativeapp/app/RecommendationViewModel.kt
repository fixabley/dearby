package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.features.analytics.Analytics
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.DictionaryTag
import com.dearby.nativeapp.entities.account.model.EligibilityProfile
import com.dearby.nativeapp.entities.account.model.RecommendationPage
import com.dearby.nativeapp.entities.account.model.RecommendationPreferences
import com.dearby.nativeapp.pages.recommendation.ChipOption
import com.dearby.nativeapp.pages.recommendation.EducationChoice
import com.dearby.nativeapp.pages.recommendation.EligibilityFormState
import com.dearby.nativeapp.pages.recommendation.EligibilityStep
import com.dearby.nativeapp.pages.recommendation.FieldNode
import com.dearby.nativeapp.pages.recommendation.FunnelStep
import com.dearby.nativeapp.pages.recommendation.RecommendationFunnelState
import com.dearby.nativeapp.pages.recommendation.RecommendationViewState
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.shared.ui.tagIcon
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24): the funnel's settings, 지원 자격 정보 and the recommendations.
 * Everything here needs a session (contract "비로그인에서 만들기·추천을 막음", 2026-10-25): settings live on the server
 * (`/v1/me/recommendation-preferences`), 지원 자격 정보 needs its own consent, and signed out nothing is read or written (old signed-out keys stay untouched).
 * Whether the funnel shows (signed in only, `DearbyApp`) is decided by the server's `completedAt` alone; no device flag is read or written;
 * the recommendation read only shows or hides the profile 추천 rows ([RecommendationViewState.available] false on any failure, 404 included).
 * The eligibility values are never logged.
 */
class RecommendationViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(RecommendationViewState())
    val state = mutable.asStateFlow()
    private val funnelState = MutableStateFlow<RecommendationFunnelState?>(null)
    val funnel = funnelState.asStateFlow()
    private val formState = MutableStateFlow<EligibilityFormState?>(null)
    val form = formState.asStateFlow()
    private var preferences = RecommendationPreferences()
    private var eligibility: EligibilityProfile? = null
    private var tags: List<DictionaryTag>? = null
    private var loadJob: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN
    private val client get() = account.client

    init {
        viewModelScope.launch {
            account.state.map { it.phase }.filter { it != AccountPhase.WORKING }.distinctUntilChanged().collect { load() }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!signedIn) {
                preferences = RecommendationPreferences()
                eligibility = null
                return@launch mutable.update { it.copy(available = false, preferencesRead = false, completed = false, eligibilitySummary = null, eligibilityConsented = false) }
            }
            mutable.update { it.copy(preferencesRead = false) }
            try {
                preferences = account.authorized { client.recommendationPreferences(it) }
                // A failed read leaves [RecommendationViewState.preferencesRead] false: the funnel is not pushed while the answer is unknown.
                mutable.update { it.copy(preferencesRead = true, completed = preferences.completedAt != null) }
                eligibility = try { account.authorized { client.eligibilityProfile(it) } } catch (e: AccountException) { if (e.error == AccountError.NOT_FOUND) null else throw e }
                show(recommendations())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Recommendations from ${ApiOrigin.current} unavailable: ${e.javaClass.simpleName} ${(e as? AccountException)?.error}")
                mutable.update { it.copy(available = false) }
            }
        }
    }
    private suspend fun recommendations(): RecommendationPage = account.authorized { client.recommendations(it) }
    private fun show(page: RecommendationPage) {
        val profile = eligibility
        mutable.update {
            it.copy(available = true,
                eligibilitySummary = profile?.summary(), eligibilityConsented = profile?.consentVersion == ConsentVersions.ELIGIBILITY)
        }
    }
    private suspend fun refresh() {
        try { show(recommendations()) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w("Dearby", "Recommendations refresh failed: ${e.javaClass.simpleName}")
        }
    }

    // ---- 추천 설정 퍼널 ----
    /** [first]: the first visit to 발견 (starts at the intro); otherwise 설정·`관심 분야·활동 종류`·`나에게 맞는 활동 찾기` reopen it at the first question. */
    fun openFunnel(first: Boolean) {
        if (funnelState.value != null) return
        funnelState.value = RecommendationFunnelState(if (first) FunnelStep.INTRO else FunnelStep.FIELDS, first,
            preferences.fieldTagIds.map(Long::toString).toSet(), preferences.categories.toSet(), preferences.traitTagIds.map(Long::toString).toSet())
        loadTags()
    }
    fun retryTags() = loadTags()
    private fun loadTags() {
        tags?.let { return applyTags(it) }
        funnelState.update { it?.copy(tagsLoading = true, tagsFailed = false) }
        viewModelScope.launch {
            try { client.tags().also { tags = it }.let(::applyTags) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { funnelState.update { it?.copy(tagsLoading = false, tagsFailed = true) } }
        }
    }
    /**
     * 분야 as a tree (contract "추천 퍼널 분야 단계(상위 → 하위)"): upper fields and fields without one, in dictionary order, each with its children
     * (a child whose upper field is missing stands alone). The saved ids come back as picks: a child id picks its upper field with that child.
     */
    private fun applyTags(all: List<DictionaryTag>) {
        val fields = all.filter { it.kind == "FIELD" }
        val ids = fields.map { it.id }.toSet()
        val children = fields.filter { it.parentId in ids }.groupBy { it.parentId }
        val tree = fields.filter { it.parentId !in ids }.map { FieldNode(chip(it), children[it.id].orEmpty().map(::chip)) }
        val parentOf = fields.filter { it.parentId in ids }.associate { it.id.toString() to it.parentId.toString() }
        funnelState.update { funnel ->
            funnel ?: return@update null
            val picked = funnel.fieldTagIds.filter { it.toLongOrNull() in ids }
            funnel.copy(fieldTree = tree, traitOptions = all.filter { tag -> tag.kind == "TRAIT" }.map(::chip), tagsLoading = false, tagsFailed = false,
                fields = picked.map { parentOf[it] ?: it }.toSet(), subFields = picked.filter { it in parentOf }.groupBy { parentOf.getValue(it) }.mapValues { it.value.toSet() })
        }
    }
    fun toggle(step: FunnelStep, id: String) = funnelState.update { funnel ->
        funnel ?: return@update null
        fun Set<String>.flip() = if (id in this) this - id else this + id
        when (step) {
            // Unpicking an upper field drops its sub-answer too.
            FunnelStep.FIELDS -> funnel.copy(fields = funnel.fields.flip(), subFields = funnel.subFields - id)
            // An upper field's id is its `전부 좋아요`: back to no child picked. A child flips; none left means `전부 좋아요` again.
            FunnelStep.SUBFIELDS -> funnel.fieldTree.firstOrNull { node -> node.option.id == id || node.children.any { it.id == id } }?.let { node ->
                val parent = node.option.id
                funnel.copy(subFields = funnel.subFields + (parent to if (id == parent) emptySet() else funnel.subFields[parent].orEmpty().flip()))
            } ?: funnel
            FunnelStep.CATEGORIES -> funnel.copy(categories = funnel.categories.flip())
            FunnelStep.TRAITS -> funnel.copy(traits = funnel.traits.flip())
            else -> funnel
        }
    }
    /** The question after this one: 분야 → each sub-question screen → 활동 종류 → 성격. */
    private fun RecommendationFunnelState.forward() = when {
        step == FunnelStep.FIELDS && subScreens.isNotEmpty() -> copy(step = FunnelStep.SUBFIELDS, subIndex = 0)
        step == FunnelStep.FIELDS -> copy(step = FunnelStep.CATEGORIES)
        step == FunnelStep.SUBFIELDS && subIndex + 1 < subScreens.size -> copy(subIndex = subIndex + 1)
        else -> copy(step = FunnelStep.entries[step.ordinal + 1])
    }
    /** The big button: next question; after the last one the settings are saved and the result shown; on the result, close. */
    fun next() {
        val funnel = funnelState.value ?: return
        when (funnel.step) {
            FunnelStep.INTRO, FunnelStep.FIELDS, FunnelStep.SUBFIELDS, FunnelStep.CATEGORIES -> funnelState.value = funnel.forward()
            FunnelStep.TRAITS -> finish(funnel)
            FunnelStep.RESULT -> funnelState.value = null
        }
    }
    /** `건너뛰기` leaves the question unanswered; `나중에` on the intro closes it, recorded as completed so it does not come back. */
    fun skip() {
        val funnel = funnelState.value ?: return
        when (funnel.step) {
            FunnelStep.INTRO -> { save(preferences.copy(completedAt = Instant.now().toString())); funnelState.value = null }
            FunnelStep.FIELDS -> funnelState.value = funnel.copy(fields = emptySet(), subFields = emptyMap(), step = FunnelStep.CATEGORIES)
            // A skipped sub-question keeps `전부 좋아요` for its upper fields.
            FunnelStep.SUBFIELDS -> funnelState.value = funnel.copy(subFields = funnel.subFields - funnel.subScreens[funnel.subIndex].map { it.option.id }.toSet()).forward()
            FunnelStep.CATEGORIES -> funnelState.value = funnel.copy(categories = emptySet(), step = FunnelStep.TRAITS)
            FunnelStep.TRAITS -> finish(funnel.copy(traits = emptySet()))
            FunnelStep.RESULT -> funnelState.value = null
        }
    }
    /** Back: the previous question; from the first question of a reopened funnel (or the intro) it closes without saving the draft. */
    fun back() {
        val funnel = funnelState.value ?: return
        if (funnel.step == FunnelStep.INTRO) return skip()
        funnelState.value = when {
            funnel.step == FunnelStep.RESULT || (funnel.step == FunnelStep.FIELDS && !funnel.first) -> null
            funnel.step == FunnelStep.SUBFIELDS && funnel.subIndex > 0 -> funnel.copy(subIndex = funnel.subIndex - 1)
            funnel.step == FunnelStep.SUBFIELDS -> funnel.copy(step = FunnelStep.FIELDS)
            funnel.step == FunnelStep.CATEGORIES && funnel.subScreens.isNotEmpty() -> funnel.copy(step = FunnelStep.SUBFIELDS, subIndex = funnel.subScreens.size - 1)
            funnel.step == FunnelStep.CATEGORIES -> funnel.copy(step = FunnelStep.FIELDS)
            else -> funnel.copy(step = FunnelStep.entries[funnel.step.ordinal - 1])
        }
    }
    private fun finish(funnel: RecommendationFunnelState) {
        funnelState.value = funnel.copy(saving = true)
        val chosen = RecommendationPreferences(funnel.fieldTagIds.mapNotNull(String::toLongOrNull), funnel.categories.toList(), funnel.traits.mapNotNull(String::toLongOrNull), Instant.now().toString())
        viewModelScope.launch {
            persist(chosen)
            val items = try { recommendations() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            items?.let(::show)
            Analytics.event("funnel_complete")
            funnelState.update { it?.copy(step = FunnelStep.RESULT, saving = false, found = items?.total, eligibilityMissing = eligibility?.summary() == null) }
        }
    }
    private fun save(chosen: RecommendationPreferences) {
        // Completed at once, so the closing funnel is not asked to open again before the write lands.
        mutable.update { it.copy(completed = true) }
        viewModelScope.launch { persist(chosen) }
    }
    /** The server (a failure says so; the funnel is not asked again this session). */
    private suspend fun persist(chosen: RecommendationPreferences) {
        preferences = chosen
        mutable.update { it.copy(completed = true) }
        try { account.authorized { client.saveRecommendationPreferences(chosen, it) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(notice = "추천 설정을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.") } }
    }
    fun dismissNotice() = mutable.update { it.copy(notice = null, eligibilityError = null) }

    // ---- 지원 자격 정보 ----
    /** After the consent sheet (or with a current consent): the questions, prefilled with what was entered. */
    fun startEligibility() {
        val profile = eligibility
        val thisYear = LocalDate.now().year
        val choice = profile?.let { p -> EducationChoice.entries.firstOrNull { it.educationStatus != null && it.educationStatus == p.educationStatus } ?: EducationChoice.EMPLOYED.takeIf { p.employment == "employed" } }
        formState.value = EligibilityFormState(EligibilityStep.STATUS, choice, profile?.schoolYear, profile?.birthYear ?: (thisYear - 22), (thisYear - 70)..(thisYear - 10))
    }
    fun choose(choice: EducationChoice) = formState.update { it?.copy(choice = choice, schoolYear = if (choice.hasYear) it.schoolYear else null, step = if (choice.hasYear) EligibilityStep.YEAR else EligibilityStep.BIRTH, error = null) }
    fun chooseYear(year: Int) = formState.update { it?.copy(schoolYear = year, step = EligibilityStep.BIRTH, error = null) }
    fun birthYear(year: Int) = formState.update { it?.copy(birthYear = year) }
    fun formNext() {
        val form = formState.value ?: return
        when (form.step) {
            EligibilityStep.STATUS -> form.choice?.let(::choose)
            EligibilityStep.YEAR -> form.schoolYear?.let(::chooseYear)
            EligibilityStep.BIRTH -> saveEligibility(form)
        }
    }
    fun formBack() = formState.update { form ->
        form ?: return@update null
        val index = form.steps.indexOf(form.step)
        if (index <= 0) null else form.copy(step = form.steps[index - 1], error = null)
    }
    private fun saveEligibility(form: EligibilityFormState) {
        val choice = form.choice ?: return
        val profile = EligibilityProfile(ConsentVersions.ELIGIBILITY, choice.educationStatus, choice.employment, form.schoolYear.takeIf { choice.hasYear }, form.birthYear)
        formState.value = form.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                account.authorized { client.saveEligibilityProfile(profile, it) }
                eligibility = profile
                formState.value = null
                mutable.update { it.copy(eligibilitySummary = profile.summary(), eligibilityConsented = true) }
                refresh()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val stale = (e as? AccountException)?.error == AccountError.BAD_REQUEST
                formState.update { it?.copy(saving = false, error = if (stale) "앱이 알고 있는 동의 버전이 오래됐어요. 앱을 업데이트한 뒤 다시 시도해 주세요." else "저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.") }
            }
        }
    }
    /** `지원 자격 정보 지우기`: the values and the consent record go (DELETE). */
    fun deleteEligibility() = viewModelScope.launch {
        try {
            account.authorized { client.deleteEligibilityProfile(it) }
            eligibility = null
            mutable.update { it.copy(eligibilitySummary = null, eligibilityConsented = false) }
            refresh()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(eligibilityError = "지원 자격 정보를 지우지 못했어요. 잠시 뒤 다시 시도해 주세요.") } }
    }
}

private fun chip(tag: DictionaryTag) = ChipOption(tag.id.toString(), tag.name, tagIcon(tag.nameEn))
/** `재학 중 · 3학년 · 2003년생`; null when nothing was entered. */
private fun EligibilityProfile.summary(): String? {
    val choice = EducationChoice.entries.firstOrNull { it.educationStatus != null && it.educationStatus == educationStatus } ?: EducationChoice.EMPLOYED.takeIf { employment == "employed" }
    return listOfNotNull(choice?.short, schoolYear?.let { if (it >= 5) "5학년 이상" else "${it}학년" }, birthYear?.let { "${it}년생" }).joinToString(" · ").ifEmpty { null }
}
