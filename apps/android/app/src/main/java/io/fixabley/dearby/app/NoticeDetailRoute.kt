package io.fixabley.dearby.app

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.features.calendarbusy.api.BusyProvider
import io.fixabley.dearby.features.calendarbusy.api.BusySession
import io.fixabley.dearby.features.calendarbusy.api.BusyConnection
import io.fixabley.dearby.features.calendarbusy.api.BusyLoad
import io.fixabley.dearby.features.calendarbusy.api.BusyQuery
import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailState
import io.fixabley.dearby.pages.noticedetail.ui.NoticeDetailSheet
import io.fixabley.dearby.shared.ui.*

@Composable
internal fun NoticeDetailRoute(notice: NoticeDetailState, provider: BusyProvider,
    onDismiss: () -> Unit, onOpenSource: (String) -> Unit, onOpenMap: (NoticeVenue) -> Unit,
    onAddToCalendar: (CalendarDraft) -> Unit,
    enabled: Boolean = false) {
    val scope = rememberCoroutineScope()
    val session = remember(provider, notice) { BusySession(provider, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled, session) { if (enabled) session.enable() else session.off() }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) session.resume()
            if (event == Lifecycle.Event.ON_STOP) session.background()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    val message = if (!enabled) null else when (session.connection) {
        BusyConnection.Revoked, BusyConnection.Restricted, BusyConnection.Denied -> "캘린더 접근을 확인하지 못했어요. 환경설정에서 권한을 확인해 주세요."
        else -> if (notice.schedules.none { it.timeline != null }) "시간이 확정된 활동이 없어 비교할 수 없어요. 신청 기간은 제외합니다."
            else "선택한 활동 날짜만 비교합니다. 신청 기간은 제외하며 겹침 없음이 참여 가능을 보장하지 않습니다."
    }
    val overlays = (if (enabled) session.results else emptyMap()).mapValues { (_, result) ->
        BusyOverlayState(when (result.load) {
            BusyLoad.Loading -> "선택 날짜의 바쁜 시간을 확인하는 중이에요."
            BusyLoad.Failed -> "선택 날짜를 조회하지 못했어요. 상세를 다시 열어 주세요."
            BusyLoad.Ready -> "선택 날짜 기준 · " + if (result.overlaps) "활동과 바쁜 시간이 겹쳐요" else "활동과 기기 바쁜 시간 겹침 없음 · 참여 가능 보장 아님"
        }, result.intervals, result.window)
    }
    NoticeDetailSheet(notice, { session.close(); onDismiss() }, onOpenSource, onOpenMap, onAddToCalendar,
        busyMessage = message, overlays = overlays, onBusyDate = { index, date ->
            notice.schedules.getOrNull(index)?.timeline?.let { interval ->
                if (interval.contains(date)) {
                    val window = BusyInterval(date.atStartOfDay(interval.zone).toInstant(), date.plusDays(1).atStartOfDay(interval.zone).toInstant())
                    session.select(index, BusyQuery(window, BusyInterval(maxOf(window.start, interval.start), minOf(window.end, interval.end))))
                }
            }
        })
}
