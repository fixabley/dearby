package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.model.RecommendationPreferences
import com.dearby.nativeapp.entities.activity.api.parseQueue
import com.dearby.nativeapp.entities.activity.model.ActivityCategory
import com.dearby.nativeapp.entities.activity.model.QueueItem
import com.dearby.nativeapp.entities.activity.model.QueueScene
import com.dearby.nativeapp.entities.activity.model.lines
import com.dearby.nativeapp.entities.activity.model.scheduleText
import com.dearby.nativeapp.entities.activity.model.stages
import com.dearby.nativeapp.pages.queue.QueueCardState
import com.dearby.nativeapp.pages.queue.QueueFact
import com.dearby.nativeapp.pages.queue.QueueIcon
import com.dearby.nativeapp.pages.queue.QueueReaction
import com.dearby.nativeapp.pages.queue.QueueSceneState
import com.dearby.nativeapp.pages.queue.QueueUndo
import com.dearby.nativeapp.pages.queue.QueueViewState
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터", 2026-10-24). Signed in, the cards are the server's queue
 * (`GET /v1/me/discovery-queue`, `더 받기` = `POST …/refill`) and every reaction goes to `POST /v1/me/recommendation-signals`; the entry hides
 * ([available] false) when the queue cannot be read, 404 before the server ships it included. Signed out, the cards are the 추천 API's answer for
 * the device settings ([preferences]) and reactions stay on the device ([store]): they leave out dismissed activities and hidden programs and put
 * `덜 보기` fields last. A `⋯` reaction waits for its `되돌리기` snackbar before it is sent or saved. Liked marks are kept for the session when signed in
 * (the server has no read of them).
 */
class QueueViewModel(private val account: AccountViewModel, private val preferences: () -> RecommendationPreferences, private val store: QueueStore) : ViewModel() {
    private val mutable = MutableStateFlow(QueueViewState())
    val state = mutable.asStateFlow()
    private val availableState = MutableStateFlow(false)
    val available = availableState.asStateFlow()
    private var items: List<QueueItem> = emptyList()
    private val liked = mutableSetOf<String>()
    private var local = LocalSignals()
    /** COMPLETE·SKIP_EARLY·OPEN_DETAIL go once per activity and session. */
    private val sent = mutableSetOf<Pair<String, String>>()
    /** Signed out: what this run already showed, so `더 받기` brings the rest. */
    private val shown = mutableSetOf<String>()
    private var pending: Pending? = null
    private var probed: JSONObject? = null
    private var undoSerial = 0
    private var job: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN
    private val client get() = account.client

    private data class Pending(val before: List<QueueItem>, val item: QueueItem, val reaction: QueueReaction)

    init {
        viewModelScope.launch {
            local = withContext(Dispatchers.IO) { store.signals() }?.let { runCatching { LocalSignals.from(JSONObject(it)) }.getOrNull() } ?: LocalSignals()
            account.state.map { it.phase }.filter { it != AccountPhase.WORKING }.distinctUntilChanged().collect { probe() }
        }
    }

    /** Whether to offer the entry: signed out it rides on the 추천 묶음; signed in the queue must answer (kept for the first open). */
    private suspend fun probe() {
        liked.clear(); sent.clear(); shown.clear(); items = emptyList(); probed = null
        if (!signedIn) { liked += local.liked; availableState.value = true; return }
        availableState.value = try { probed = account.authorized { client.discoveryQueue(it) }; true } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w("Dearby", "Discovery queue from ${ApiOrigin.current} unavailable: ${e.javaClass.simpleName}")
            false
        }
    }

    fun open() {
        mutable.value = QueueViewState()
        load(refill = false)
    }
    fun retry() = load(refill = false)
    fun refill() = load(refill = true)

    private fun load(refill: Boolean) {
        job?.cancel()
        mutable.update { it.copy(loading = true, failed = false, refilling = refill) }
        job = viewModelScope.launch {
            try {
                val body = when {
                    signedIn && refill -> account.authorized { client.refillDiscoveryQueue(it) }
                    signedIn -> probed ?: account.authorized { client.discoveryQueue(it) }
                    else -> client.recommendationsBody(preferences(), null, if (refill) 50 else 30)
                }
                probed = null
                val got = parseQueue(body, ApiOrigin.current).let { if (signedIn) it else signedOutQueue(it, refill) }
                if (refill && got.isEmpty()) return@launch mutable.update { it.copy(loading = false, refilling = false, exhausted = true) }
                items = got
                shown += got.map { it.activity.id }
                show(0, move = true) { it.copy(loading = false, refilling = false, failed = false, exhausted = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Discovery queue load failed: ${e.javaClass.simpleName}")
                mutable.update { it.copy(loading = false, refilling = false, failed = true) }
            }
        }
    }
    /** The device's reactions applied to a 추천 answer: dismissed and ineligible activities and hidden programs out, `덜 보기` fields last; ten. */
    private fun signedOutQueue(all: List<QueueItem>, refill: Boolean) = all
        .filter { it.activity.id !in local.excluded && it.program.id !in local.hiddenPrograms && !(refill && it.activity.id in shown) }
        .sortedBy { item -> item.activity.fieldTags.any { it.id in local.lessFields } }
        .take(10)

    private fun show(index: Int, move: Boolean, change: (QueueViewState) -> QueueViewState = { it }) = mutable.update {
        change(it).copy(cards = items.map(::card), index = index.coerceIn(0, items.size), moves = if (move) it.moves + 1 else it.moves)
    }
    fun page(index: Int) = mutable.update { it.copy(index = index) }

    /** ♥: [force] true is the double tap (only ever likes); null toggles. */
    fun like(id: String, force: Boolean?) {
        val on = force ?: (id !in liked)
        if (on == id in liked) return
        if (on) liked += id else liked -= id
        signal(id, if (on) "LIKE" else "UNLIKE")
        if (!signedIn) saveLocal(local.copy(liked = liked.toSet()))
        show(state.value.index, move = false)
    }

    /** A `⋯` choice: the card (every card of the program for 숨기기) leaves at once; [commit] sends it, [undo] puts the queue back. */
    fun react(id: String, reaction: QueueReaction) {
        commit()
        val item = items.firstOrNull { it.activity.id == id } ?: return
        val index = items.indexOf(item)
        pending = Pending(items, item, reaction)
        items = items.filterNot { it.activity.id == id || (reaction == QueueReaction.HIDE_PROGRAM && it.program.id == item.program.id) }
        show(minOf(index, items.size), move = true) { it.copy(undo = QueueUndo(reaction.done, ++undoSerial)) }
    }
    fun undo() {
        val undone = pending ?: return
        pending = null
        items = undone.before
        show(items.indexOf(undone.item), move = true) { it.copy(undo = null) }
    }
    fun commit() {
        val done = pending ?: return
        pending = null
        mutable.update { it.copy(undo = null) }
        val activity = done.item.activity
        val field = fieldOf(done.item)
        when (done.reaction) {
            QueueReaction.DISMISS -> signal(activity.id, "DISMISS")
            QueueReaction.LESS_FIELD -> signal(activity.id, "LESS_FIELD", field?.id)
            QueueReaction.HIDE_PROGRAM -> signal(activity.id, "HIDE_PROGRAM")
            QueueReaction.INELIGIBLE -> signal(activity.id, "INELIGIBLE")
        }
        if (!signedIn) saveLocal(when (done.reaction) {
            QueueReaction.DISMISS, QueueReaction.INELIGIBLE -> local.copy(excluded = local.excluded + activity.id)
            QueueReaction.LESS_FIELD -> field?.let { local.copy(lessFields = local.lessFields + it.id) } ?: local
            QueueReaction.HIDE_PROGRAM -> local.copy(hiddenPrograms = local.hiddenPrograms + done.item.program.id)
        })
    }
    /** Leaving the queue sends a reaction still waiting on its snackbar. */
    fun close() = commit()

    fun complete(id: String) = once(id, "COMPLETE")
    fun skipEarly(id: String) = once(id, "SKIP_EARLY")
    fun openedDetail(id: String) = once(id, "OPEN_DETAIL")
    private fun once(id: String, kind: String) { if (sent.add(id to kind)) signal(id, kind) }

    /** Signed in only (contract: 비로그인은 보내지 않는다); a failure is logged and dropped. */
    private fun signal(id: String, kind: String, tagId: String? = null) {
        if (!signedIn) return
        viewModelScope.launch {
            try { account.authorized { client.sendRecommendationSignal(id, kind, tagId, it) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Dearby", "Recommendation signal $kind failed: ${e.javaClass.simpleName}") }
        }
    }
    private fun saveLocal(next: LocalSignals) {
        local = next
        viewModelScope.launch(Dispatchers.IO) { store.saveSignals(next.toJson().toString()) }
    }

    /**
     * The server's scenes when it sent them; otherwise HOOK, HIGHLIGHTS when there are at least two lines (`highlights`, else 기간·장소 — the public
     * API has no 비용) and DEADLINE.
     */
    private fun card(item: QueueItem): QueueCardState {
        val activity = item.activity
        val program = item.program
        val field = fieldOf(item)
        val event = activity.category == ActivityCategory.CONFERENCE
        val heading = if (event) "행사까지" else "마감까지"
        val scenes = item.scenes?.map { scene ->
            when (scene) {
                is QueueScene.Hook -> QueueSceneState.Hook(scene.hook, scene.name)
                is QueueScene.Highlights -> QueueSceneState.Highlights(scene.items.map { QueueFact(it.icon.queueIcon(), it.text) })
                is QueueScene.Eligibility -> QueueSceneState.Eligibility(scene.text)
                is QueueScene.Benefit -> QueueSceneState.Benefit(scene.text)
                is QueueScene.Deadline -> QueueSceneState.Deadline(scene.label ?: heading, scene.dday, scene.deadlineText, scene.reasons)
            }
        } ?: run {
            // A conference counts to the event (`행사까지`), everything else to the application deadline.
            val stages = activity.stages()
            val deadline = (if (event) stages.lastOrNull()?.takeIf { activity.activitySchedule != null } else stages.firstOrNull { it.kind == "APPLICATION" })?.takeIf { it.day != null }
            val left = deadline?.day?.let { ChronoUnit.DAYS.between(LocalDate.now(SEOUL), it) }
            val facts = activity.highlights.take(3).map { QueueFact(it.icon.queueIcon(), it.text) }.ifEmpty {
                listOfNotNull(
                    (activity.activitySchedule?.scheduleText()?.ifBlank { null } ?: activity.dateLabel.ifBlank { null })?.let { QueueFact(QueueIcon.PERIOD, it) },
                    activity.venue?.let { QueueFact(QueueIcon.PLACE, it) },
                )
            }
            listOfNotNull(
                QueueSceneState.Hook(activity.hook ?: activity.name, if (activity.hook != null) activity.name else program.name),
                facts.takeIf { it.size >= 2 }?.let(QueueSceneState::Highlights),
                QueueSceneState.Deadline(heading, left?.toInt(),
                    listOfNotNull(deadline?.dateText, activity.eligibility?.lines()?.firstOrNull()).joinToString(" · ").ifEmpty { null }, item.reasons),
            )
        }
        return QueueCardState(
            activity.id, program.organization.ifBlank { activity.organization }, program.logoUrl, program.coverUrl, (program.id.hashCode().toLong() and 0xffff).rem(360).toFloat(),
            listOfNotNull(activity.category?.label, field?.name).joinToString(" · ").ifEmpty { null },
            scenes, activity.id in liked, field?.name,
        )
    }
    private fun fieldOf(item: QueueItem) = item.activity.fieldTags.firstOrNull() ?: item.program.fieldTags.firstOrNull()

    private companion object { val SEOUL: ZoneId = ZoneId.of("Asia/Seoul") }
}

private fun String.queueIcon() = when (this) {
    "cost" -> QueueIcon.COST; "period" -> QueueIcon.PERIOD; "team" -> QueueIcon.TEAM; "mentor" -> QueueIcon.MENTOR
    "award" -> QueueIcon.AWARD; "place" -> QueueIcon.PLACE; "ticket" -> QueueIcon.TICKET; else -> QueueIcon.TALK
}

/** Signed-out reactions (contract "비로그인은 … 신호는 기기에만 둔다"), as ids: liked and excluded activities, hidden programs, `덜 보기` field tags. */
private data class LocalSignals(val liked: Set<String> = emptySet(), val excluded: Set<String> = emptySet(), val hiddenPrograms: Set<String> = emptySet(), val lessFields: Set<String> = emptySet()) {
    fun toJson(): JSONObject = JSONObject().put("liked", JSONArray(liked)).put("excluded", JSONArray(excluded)).put("hiddenPrograms", JSONArray(hiddenPrograms)).put("lessFields", JSONArray(lessFields))
    companion object {
        fun from(json: JSONObject) = LocalSignals(json.ids("liked"), json.ids("excluded"), json.ids("hiddenPrograms"), json.ids("lessFields"))
        private fun JSONObject.ids(key: String) = optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) }.toSet() } ?: emptySet()
    }
}

/** The app's settings store (a new key in the existing file) for the signed-out reactions. */
interface QueueStore {
    fun signals(): String?
    fun saveSignals(json: String)
}
