package io.fixabley.dearby.app

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
    permissionRequest: ((() -> Unit) -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val session = remember(provider, notice) { BusySession(provider, scope) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var requestToken by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestToken?.let(session::permissionResult); requestToken = null
    }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) session.resume()
            if (event == Lifecycle.Event.ON_STOP) session.background()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    val state = BusyDisplayState(session.enabled, when (session.connection) {
        BusyConnection.Off -> "기기 캘린더 연결 안 됨 · 신청 기간은 참석 겹침에서 제외합니다."
        BusyConnection.Consent -> "연결 목적을 확인해 주세요."
        BusyConnection.Requesting -> "캘린더 읽기 권한을 기다리고 있어요."
        BusyConnection.Denied -> "캘린더 권한이 거절되어 바쁜 시간을 확인하지 못했어요. 다시 켜거나 앱 설정에서 허용할 수 있어요."
        BusyConnection.Restricted -> "기기 정책으로 캘린더 접근이 제한되어 있어요."
        BusyConnection.Revoked -> "캘린더 권한이 없어 바쁜 시간을 지웠어요."
        BusyConnection.Active -> if (notice.schedules.none { it.timeline != null }) "시간이 확정된 활동이 없어 비교할 수 없어요. 신청 기간은 제외합니다." else "확정된 활동의 선택 날짜만 비교합니다. 신청 기간은 제외하며, 기기 일정과 겹치지 않아도 참여를 보장하지 않습니다."
    }, session.connection == BusyConnection.Consent, session.connection == BusyConnection.Requesting,
        session.connection in listOf(BusyConnection.Denied, BusyConnection.Restricted, BusyConnection.Revoked))
    val overlays = session.results.mapValues { (_, result) ->
        BusyOverlayState(when (result.load) {
            BusyLoad.Loading -> "선택 날짜의 바쁜 시간을 확인하는 중이에요."
            BusyLoad.Failed -> "선택 날짜를 조회하지 못했어요. 다시 조회해 주세요."
            BusyLoad.Ready -> "선택 날짜 기준 · " + if (result.overlaps) "활동과 바쁜 시간이 겹쳐요" else "활동과 기기 바쁜 시간 겹침 없음 · 참여 가능 보장 아님"
        }, result.intervals, result.window)
    }.toMutableMap()
    NoticeDetailSheet(notice, { session.close(); onDismiss() }, onOpenSource, onOpenMap, onAddToCalendar,
        state, overlays, { if (it) session.enable() else session.off() }, {
            session.confirm()?.let { token ->
                requestToken = token
                if (permissionRequest != null) permissionRequest { session.permissionResult(token) }
                else launcher.launch(Manifest.permission.READ_CALENDAR)
            }
        }, {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }, session::retry, { index, date ->
            notice.schedules.getOrNull(index)?.timeline?.let { interval ->
                if (interval.contains(date)) {
                    val window = BusyInterval(date.atStartOfDay(interval.zone).toInstant(), date.plusDays(1).atStartOfDay(interval.zone).toInstant())
                    session.select(index, BusyQuery(window, BusyInterval(maxOf(window.start, interval.start), minOf(window.end, interval.end))))
                }
            }
        })
}
