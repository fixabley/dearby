package io.fixabley.dearby.app

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.fixabley.dearby.pages.settings.model.SettingsState
import io.fixabley.dearby.pages.settings.ui.SettingsSheet
import io.fixabley.dearby.pages.settings.ui.CalendarWelcomeDialog

@Composable
internal fun CalendarSettingsHost(controller: CalendarSettingsController, settingsOpen: Boolean, onDismiss: () -> Unit,
    permissionRequest: ((() -> Unit) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var requestToken by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestToken?.let(controller::permissionResult); requestToken = null
    }
    val enable: () -> Unit = {
        controller.enable()?.let { token ->
            requestToken = token
            try {
                if (permissionRequest != null) permissionRequest { controller.permissionResult(token) }
                else launcher.launch(Manifest.permission.READ_CALENDAR)
            } catch (_: Exception) { controller.failed(token) }
        }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.resume()
            if (event == Lifecycle.Event.ON_STOP) controller.background()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.background() }
    }
    if (!controller.firstPromptHandled) CalendarWelcomeDialog(enable, controller::later)
    if (settingsOpen) SettingsSheet(SettingsState(controller.enabled, controller.status == CalendarSettingsStatus.Waiting,
        when (controller.status) {
            CalendarSettingsStatus.Off -> "꺼짐 · 상세에서 기기 일정을 조회하지 않습니다."
            CalendarSettingsStatus.Active -> "켜짐 · 선택한 활동 날짜만 비교하며 신청 기간은 제외합니다."
            CalendarSettingsStatus.Waiting -> "캘린더 읽기 권한을 기다리고 있어요."
            CalendarSettingsStatus.Denied -> "권한이 거절되어 확인 기능이 꺼져 있어요. 다시 켜거나 앱 권한 설정을 확인해 주세요."
            CalendarSettingsStatus.Restricted -> "기기 정책으로 캘린더 접근이 제한되어 있어요."
            CalendarSettingsStatus.Revoked -> "캘린더 권한이 없어 확인 기능을 껐어요."
            CalendarSettingsStatus.Failed -> "권한 요청을 열지 못했어요. 다시 켜거나 앱 권한 설정을 확인해 주세요."
        }, controller.status in setOf(CalendarSettingsStatus.Denied, CalendarSettingsStatus.Restricted, CalendarSettingsStatus.Revoked, CalendarSettingsStatus.Failed)),
        { if (it) enable() else controller.disable() }, {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }, onDismiss)
}
