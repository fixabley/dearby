package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.activity.api.parseInbox
import com.dearby.nativeapp.entities.activity.model.InboxGroup
import com.dearby.nativeapp.pages.inbox.InboxCard
import com.dearby.nativeapp.pages.inbox.InboxPhase
import com.dearby.nativeapp.pages.inbox.InboxRow
import com.dearby.nativeapp.pages.inbox.InboxViewState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
 * 알림 목록 (contract "알림 목록(발견 우상단 종)", 2026-10-25), signed in only: `GET /v1/me/notifications` page by page, the bell's unread count
 * (`…/unread-count`, read at sign-in, when the app comes back and after reads), `…/{id}/read` (a row, or the push it came with) and `…/read-all`.
 * Reads change the screen first; a failure is only logged (the next read corrects it). A 404 (not deployed yet) is an empty list and no badge.
 */
class InboxViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(InboxViewState())
    val state = mutable.asStateFlow()
    private var groups: List<InboxGroup> = emptyList()
    private var job: Job? = null
    private val signedIn get() = account.ready.value

    init {
        viewModelScope.launch {
            account.ready.map { if (it) AccountPhase.SIGNED_IN else AccountPhase.SIGNED_OUT }.collect { phase ->
                groups = emptyList()
                mutable.value = InboxViewState()
                if (phase == AccountPhase.SIGNED_IN) refreshCount()
            }
        }
    }

    fun refreshCount() {
        if (!signedIn) return
        viewModelScope.launch {
            val count = call("unread count") { account.client.unreadNotifications(it) } ?: 0
            mutable.update { it.copy(unread = count) }
        }
    }

    fun open() { load(null) }
    fun loadMore() { state.value.nextCursor?.takeIf { job?.isActive != true }?.let(::load) }
    private fun load(cursor: String?) {
        if (!signedIn) return
        job?.cancel()
        if (cursor == null && state.value.phase != InboxPhase.LOADED) mutable.update { it.copy(phase = InboxPhase.LOADING) }
        job = viewModelScope.launch {
            try {
                val page = parseInbox(account.authorized { account.client.notifications(cursor, it) }, ApiOrigin.current)
                groups = (if (cursor == null) emptyList() else groups) + page.groups
                show(page.nextCursor)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Notifications load failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
                if ((e as? AccountException)?.error == AccountError.NOT_FOUND) { groups = emptyList(); show(null) }
                else if (cursor == null) mutable.update { it.copy(phase = InboxPhase.FAILED) }
            }
        }
        refreshCount()
    }

    /** A row was opened (or its push tapped): read at once on screen, then on the server. */
    fun read(id: String) {
        val wasUnread = groups.any { group -> group.items.any { it.id == id && !it.read } }
        groups = groups.map { group -> if (group.items.none { it.id == id && !it.read }) group else group.copy(unread = (group.unread - 1).coerceAtLeast(0), items = group.items.map { if (it.id == id) it.copy(read = true) else it }) }
        if (wasUnread) mutable.update { it.copy(unread = (it.unread - 1).coerceAtLeast(0)) }
        show(state.value.nextCursor, keepPhase = true)
        viewModelScope.launch { call("read") { account.client.readNotification(id, it) }; if (!wasUnread) refreshCount() }
    }
    fun readAll() {
        groups = groups.map { group -> group.copy(unread = 0, items = group.items.map { it.copy(read = true) }) }
        mutable.update { it.copy(unread = 0) }
        show(state.value.nextCursor, keepPhase = true)
        viewModelScope.launch { call("read all") { account.client.readAllNotifications(it) } }
    }

    private suspend fun <T> call(what: String, block: suspend (com.dearby.nativeapp.entities.account.model.AccountSession) -> T): T? = try {
        if (signedIn) account.authorized(block) else null
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Log.w("Dearby", "Notifications $what failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
        null
    }

    private fun show(next: String?, keepPhase: Boolean = false) {
        val now = Instant.now()
        val cards = groups.map { group ->
            InboxCard(group.program.id, group.program.name, group.program.organization, group.program.logoUrl, group.unread,
                group.items.map { InboxRow(it.id, it.activityId, it.activityName, kindLabel(it.kind), received(it.createdAt, now), !it.read) },
                group.items.mapNotNull { it.createdAt }.maxOrNull())
        }
        val (recent, older) = cards.partition { it.latest != null && Duration.between(it.latest, now).toDays() < 7 }
        mutable.update { it.copy(phase = if (keepPhase && it.phase != InboxPhase.LOADED) it.phase else InboxPhase.LOADED, recent = recent, older = older, nextCursor = next) }
    }
}

private val timeText = DateTimeFormatter.ofPattern("HH:mm")
private val dayText = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
/** `오늘 08:00` · `어제` · `10월 21일`, in the device's time zone (the server sends at 8 there). */
private fun received(at: Instant?, now: Instant): String {
    at ?: return ""
    val zone = ZoneId.systemDefault()
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) { today -> "오늘 " + timeText.format(at.atZone(zone)); today.minusDays(1) -> "어제"; else -> dayText.format(day) }
}
private fun kindLabel(kind: String) = when (kind) {
    "NEW" -> "새 모집"; "APPLICATION_START" -> "모집 시작"; "DEADLINE_EVE" -> "마감 D-1"; "DEADLINE" -> "오늘 마감"
    "ANNOUNCEMENT" -> "발표"; "INTERVIEW" -> "면접"; "EVENT" -> "행사"; "ACTIVITY_START" -> "활동 시작"; else -> "알림"
}
