package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.activity.api.parseQueue
import com.dearby.nativeapp.features.analytics.Analytics
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터", 2026-10-24). Signed in, the cards are the server's queue
 * (`GET /v1/me/discovery-queue`, `더 받기` = `POST …/refill`) and every reaction goes to `POST /v1/me/recommendation-signals`. The entry always
 * shows; a failed read (404 before the server ships it included) is the queue screen's retry state. Signed out there is no queue (contract
 * "비로그인에서 만들기·추천을 막음", 2026-10-25: the entry opens sign-in), and the old device reactions are neither read nor written.
 * A `⋯` reaction waits for its `되돌리기` snackbar before it is sent. The ♥ and `+` belong to 즐겨찾기 and 내 활동 (QueueRoute).
 */
class QueueViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(QueueViewState())
    val state = mutable.asStateFlow()
    private var items: List<QueueItem> = emptyList()
    /** COMPLETE·SKIP_EARLY·OPEN_DETAIL go once per activity and session. */
    private val sent = mutableSetOf<Pair<String, String>>()
    private var pending: Pending? = null
    private var undoSerial = 0
    private var job: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN
    private val client get() = account.client

    private data class Pending(val before: List<QueueItem>, val item: QueueItem, val reaction: QueueReaction)

    init {
        viewModelScope.launch {
            account.state.map { it.phase }.filter { it != AccountPhase.WORKING }.distinctUntilChanged().collect { reset() }
        }
    }

    /** A new account state starts over. */
    private fun reset() {
        sent.clear(); items = emptyList()
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
                val body = account.authorized { if (refill) client.refillDiscoveryQueue(it) else client.discoveryQueue(it) }
                val got = parseQueue(body, ApiOrigin.current)
                if (refill && got.isEmpty()) return@launch mutable.update { it.copy(loading = false, refilling = false, exhausted = true) }
                items = got
                if (!refill) {
                    ended = false; viewed.clear()
                    val seen = body.optJSONArray("seen")?.length() ?: body.optInt("seen", 0)
                    Analytics.event("queue_open", "size" to got.size.toString(), "seen" to seen.toString())
                }
                show(0, move = true) { it.copy(loading = false, refilling = false, failed = false, exhausted = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Discovery queue load failed: ${e.javaClass.simpleName}")
                mutable.update { it.copy(loading = false, refilling = false, failed = true) }
            }
        }
    }
    private fun show(index: Int, move: Boolean, change: (QueueViewState) -> QueueViewState = { it }) = mutable.update {
        change(it).copy(cards = items.map(::card), index = index.coerceIn(0, items.size), moves = if (move) it.moves + 1 else it.moves)
    }
    fun page(index: Int) {
        mutable.update { it.copy(index = index) }
        if (index == items.size && items.isNotEmpty() && !ended) { ended = true; Analytics.event("queue_end", "viewed" to viewed.size.toString()) }
    }

    // analytics-v1 "맞춤 대기열 도달": positions count from 1 in today's queue.
    private var ended = false
    private val viewed = mutableSetOf<String>()
    private fun position(id: String) = (items.indexOfFirst { it.activity.id == id } + 1).toString()
    private fun card(id: String) = Tracking.activity(model(id), id)
    /** A card stayed centred for a second. */
    fun cardViewed(id: String) { if (viewed.add(id)) Analytics.event("queue_card_view", "position" to position(id), *card(id)) }
    /** The ♥ turned a card into a favorite. */
    fun liked(id: String) = Analytics.event("queue_like", "position" to position(id), *card(id))
    /** The queue closed by the user before its end page. */
    fun exit() { if (!ended && items.isNotEmpty()) Analytics.event("queue_exit", "position" to (state.value.index + 1).coerceAtMost(items.size).toString()) }

    /** The activity behind a card, for 즐겨찾기 and `+` (the ♥ is the 즐겨찾기 heart; the server records LIKE/UNLIKE from it, 2026-10-25). */
    fun model(id: String) = items.firstOrNull { it.activity.id == id }?.activity

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
        Analytics.event("queue_dismiss", "reason" to done.reaction.name.lowercase(), "position" to (done.before.indexOf(done.item) + 1).toString(), *Tracking.activity(activity, activity.id))
        when (done.reaction) {
            QueueReaction.DISMISS -> signal(activity.id, "DISMISS")
            QueueReaction.LESS_FIELD -> signal(activity.id, "LESS_FIELD", field?.id)
            QueueReaction.HIDE_PROGRAM -> signal(activity.id, "HIDE_PROGRAM")
            QueueReaction.INELIGIBLE -> signal(activity.id, "INELIGIBLE")
        }
    }
    /** Leaving the queue sends a reaction still waiting on its snackbar. */
    fun close() = commit()

    fun complete(id: String) {
        if (sent.add(id to "COMPLETE")) {
            signal(id, "COMPLETE")
            Analytics.event("queue_card_complete", "position" to position(id), "scenes" to (state.value.cards.firstOrNull { it.id == id }?.scenes?.size ?: 0).toString())
        }
    }
    fun skipEarly(id: String) = once(id, "SKIP_EARLY")
    fun openedDetail(id: String) {
        Analytics.event("queue_detail", "position" to position(id), "activity_id" to id)
        once(id, "OPEN_DETAIL")
    }
    private fun once(id: String, kind: String) { if (sent.add(id to kind)) signal(id, kind) }

    /** Signed in only (the session can lapse while the queue is open); a failure is logged and dropped. */
    private fun signal(id: String, kind: String, tagId: String? = null) {
        if (!signedIn) return
        viewModelScope.launch {
            try { account.authorized { client.sendRecommendationSignal(id, kind, tagId, it) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Dearby", "Recommendation signal $kind failed: ${e.javaClass.simpleName}") }
        }
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
                is QueueScene.Eligibility -> QueueSceneState.Eligibility(scene.text, scene.items.map { QueueFact(it.icon.eligibilityIcon(), it.text) })
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
            scenes, field?.name,
        )
    }
    private fun fieldOf(item: QueueItem) = item.activity.fieldTags.firstOrNull() ?: item.program.fieldTags.firstOrNull()

    private companion object { val SEOUL: ZoneId = ZoneId.of("Asia/Seoul") }
}

private fun String.queueIcon() = when (this) {
    "cost" -> QueueIcon.COST; "period" -> QueueIcon.PERIOD; "team" -> QueueIcon.TEAM; "mentor" -> QueueIcon.MENTOR
    "award" -> QueueIcon.AWARD; "place" -> QueueIcon.PLACE; "ticket" -> QueueIcon.TICKET; else -> QueueIcon.TALK
}

private fun String.eligibilityIcon() = when (this) {
    "age" -> QueueIcon.AGE; "education" -> QueueIcon.EDUCATION; "schoolYear" -> QueueIcon.SCHOOL_YEAR; "employment" -> QueueIcon.EMPLOYMENT
    "region" -> QueueIcon.PLACE; "major" -> QueueIcon.MAJOR; "nationality" -> QueueIcon.NATIONALITY; "experience" -> QueueIcon.EXPERIENCE
    "team" -> QueueIcon.TEAM; else -> QueueIcon.OTHER
}
