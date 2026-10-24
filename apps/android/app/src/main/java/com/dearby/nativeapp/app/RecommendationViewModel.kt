package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.DictionaryTag
import com.dearby.nativeapp.entities.account.model.EligibilityProfile
import com.dearby.nativeapp.entities.account.model.Recommendation
import com.dearby.nativeapp.entities.account.model.RecommendationPage
import com.dearby.nativeapp.entities.account.model.RecommendationPreferences
import com.dearby.nativeapp.entities.account.model.toRecommendationPreferences
import com.dearby.nativeapp.pages.recommendation.ChipGroup
import com.dearby.nativeapp.pages.recommendation.ChipOption
import com.dearby.nativeapp.pages.recommendation.EducationChoice
import com.dearby.nativeapp.pages.recommendation.EligibilityFormState
import com.dearby.nativeapp.pages.recommendation.EligibilityStep
import com.dearby.nativeapp.pages.recommendation.FunnelStep
import com.dearby.nativeapp.pages.recommendation.RecommendationFunnelState
import com.dearby.nativeapp.pages.recommendation.RecommendationViewState
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.widgets.recommendation.recommendationRows.RecommendationItemState
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

/**
 * 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24): the funnel's settings, 지원 자격 정보 and the recommendations.
 * Settings live on the server when signed in (`/v1/me/recommendation-preferences`) and in the app's settings store ([store], new keys)
 * when signed out; on sign-in a device copy is uploaded once if the server has none. 지원 자격 정보 needs a session and its own consent.
 * Whether the funnel shows is decided by the device's completion flag alone (a server `completedAt` is copied into it, so signing out does not bring
 * the funnel back); the recommendation read only shows or hides the 추천 묶음 ([RecommendationViewState.available] false on any failure, 404 included).
 * The eligibility values are never logged.
 */
class RecommendationViewModel(private val account: AccountViewModel, private val store: RecommendationStore) : ViewModel() {
    private val mutable = MutableStateFlow(RecommendationViewState())
    val state = mutable.asStateFlow()
    private val funnelState = MutableStateFlow<RecommendationFunnelState?>(null)
    val funnel = funnelState.asStateFlow()
    private val formState = MutableStateFlow<EligibilityFormState?>(null)
    val form = formState.asStateFlow()
    private var preferences = RecommendationPreferences()
    /** The settings in use (server or device copy), for 오늘의 맞춤 대기열 while signed out. */
    val currentPreferences get() = preferences
    private var eligibility: EligibilityProfile? = null
    private var tags: List<DictionaryTag>? = null
    private var loadJob: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN
    private val client get() = account.client

    init {
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) { store.completed() }
            mutable.update { it.copy(localLoaded = true, completed = it.completed || done) }
            account.state.map { it.phase }.filter { it != AccountPhase.WORKING }.distinctUntilChanged().collect { load() }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { store.preferences() }?.let { runCatching { JSONObject(it).toRecommendationPreferences() }.getOrNull() }
            try {
                if (signedIn) {
                    val server = account.authorized { client.recommendationPreferences(it) }
                    preferences = if (server.completedAt == null && local?.completedAt != null) local.also { copy -> account.authorized { client.saveRecommendationPreferences(copy, it) } } else server
                    if (preferences.completedAt != null) markCompleted()
                    eligibility = try { account.authorized { client.eligibilityProfile(it) } } catch (e: AccountException) { if (e.error == AccountError.NOT_FOUND) null else throw e }
                } else {
                    preferences = local ?: RecommendationPreferences()
                    if (preferences.completedAt != null) markCompleted()
                    eligibility = null
                }
                show(recommendations())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Recommendations from ${ApiOrigin.current} unavailable: ${e.javaClass.simpleName} ${(e as? AccountException)?.error}")
                mutable.update { it.copy(available = false, items = emptyList()) }
            }
        }
    }
    private suspend fun recommendations(): RecommendationPage =
        if (signedIn) account.authorized { client.recommendations(null, it) } else client.recommendations(preferences, null)
    private fun show(page: RecommendationPage) {
        val profile = eligibility
        mutable.update {
            it.copy(available = true, personalized = !preferences.empty, items = page.items.map(::item),
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
    /** Fields grouped by their upper field (contract "하위 분야"); upper fields without sub-fields and orphans go under `그 밖에`. */
    private fun applyTags(all: List<DictionaryTag>) {
        val fields = all.filter { it.kind == "FIELD" }
        val children = fields.filter { it.parentId != null }.groupBy { it.parentId }
        val roots = fields.filter { it.parentId == null }
        val grouped = roots.filter { it.id in children }.map { root -> ChipGroup(root.name, children.getValue(root.id).map(::chip)) }
        val rootIds = roots.map { it.id }.toSet()
        val loose = roots.filter { it.id !in children } + fields.filter { it.parentId != null && it.parentId !in rootIds }
        val groups = grouped + if (loose.isEmpty()) emptyList() else listOf(ChipGroup(if (grouped.isEmpty()) "분야" else "그 밖에", loose.map(::chip)))
        funnelState.update { it?.copy(fieldGroups = groups, traitOptions = all.filter { tag -> tag.kind == "TRAIT" }.map(::chip), tagsLoading = false, tagsFailed = false) }
    }
    fun toggle(step: FunnelStep, id: String) = funnelState.update { funnel ->
        funnel ?: return@update null
        fun Set<String>.flip() = if (id in this) this - id else this + id
        when (step) {
            FunnelStep.FIELDS -> funnel.copy(fields = funnel.fields.flip())
            FunnelStep.CATEGORIES -> funnel.copy(categories = funnel.categories.flip())
            FunnelStep.TRAITS -> funnel.copy(traits = funnel.traits.flip())
            else -> funnel
        }
    }
    /** The big button: next question; after the last one the settings are saved and the result shown; on the result, close. */
    fun next() {
        val funnel = funnelState.value ?: return
        when (funnel.step) {
            FunnelStep.INTRO, FunnelStep.FIELDS, FunnelStep.CATEGORIES -> funnelState.value = funnel.copy(step = FunnelStep.entries[funnel.step.ordinal + 1])
            FunnelStep.TRAITS -> finish(funnel)
            FunnelStep.RESULT -> funnelState.value = null
        }
    }
    /** `건너뛰기` leaves the question unanswered; `나중에` on the intro closes it, recorded as completed so it does not come back. */
    fun skip() {
        val funnel = funnelState.value ?: return
        when (funnel.step) {
            FunnelStep.INTRO -> { save(preferences.copy(completedAt = Instant.now().toString())); funnelState.value = null }
            FunnelStep.FIELDS -> funnelState.value = funnel.copy(fields = emptySet(), step = FunnelStep.CATEGORIES)
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
            else -> funnel.copy(step = FunnelStep.entries[funnel.step.ordinal - 1])
        }
    }
    private fun finish(funnel: RecommendationFunnelState) {
        funnelState.value = funnel.copy(saving = true)
        val chosen = RecommendationPreferences(funnel.fields.mapNotNull(String::toLongOrNull), funnel.categories.toList(), funnel.traits.mapNotNull(String::toLongOrNull), Instant.now().toString())
        viewModelScope.launch {
            persist(chosen)
            val items = try { recommendations() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            items?.let(::show)
            funnelState.update { it?.copy(step = FunnelStep.RESULT, saving = false, found = items?.total, preview = items?.items.orEmpty().take(3).map(::item), eligibilityMissing = eligibility?.summary() == null) }
        }
    }
    private fun save(chosen: RecommendationPreferences) {
        // Completed at once, so the closing funnel is not asked to open again before the write lands.
        mutable.update { it.copy(completed = true) }
        viewModelScope.launch { persist(chosen) }
    }
    /** The device flag that keeps the funnel from coming back, for both signed-in and signed-out use. */
    private suspend fun markCompleted() {
        mutable.update { it.copy(completed = true) }
        withContext(Dispatchers.IO) { store.markCompleted() }
    }
    /** Signed in → the server (a failure says so; the funnel is not asked again this session); signed out → the device copy. */
    private suspend fun persist(chosen: RecommendationPreferences) {
        preferences = chosen
        mutable.update { it.copy(personalized = !chosen.empty) }
        markCompleted()
        if (!signedIn) return withContext(Dispatchers.IO) { store.savePreferences(chosen.toJson().toString()) }
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

/** The app's settings store (contract "추천 설정 저장": new keys in the existing file): the signed-out settings JSON and the funnel's completion flag. */
interface RecommendationStore {
    fun preferences(): String?
    fun savePreferences(json: String)
    fun completed(): Boolean
    fun markCompleted()
}

private fun chip(tag: DictionaryTag) = ChipOption(tag.id.toString(), tag.name)
private fun item(recommendation: Recommendation) = RecommendationItemState(recommendation.activityId, recommendation.name,
    "${recommendation.program} · ${recommendation.organization}", recommendation.reasons, recommendation.logoUrl)
/** `재학 중 · 3학년 · 2003년생`; null when nothing was entered. */
private fun EligibilityProfile.summary(): String? {
    val choice = EducationChoice.entries.firstOrNull { it.educationStatus != null && it.educationStatus == educationStatus } ?: EducationChoice.EMPLOYED.takeIf { employment == "employed" }
    return listOfNotNull(choice?.short, schoolYear?.let { if (it >= 5) "5학년 이상" else "${it}학년" }, birthYear?.let { "${it}년생" }).joinToString(" · ").ifEmpty { null }
}
