package com.dearby.nativeapp.features.live

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/**
 * What can keep 행사 안내 from showing live (2026-10-27 "앱 내에서 유도"): [promotionOff] Android 16+'s per-app 실시간 알림 (live updates) is off
 * ([NotificationManagerCompat.canPostPromotedNotifications]); [samsung] One UI, whose Now Bar has its own per-app list that apps cannot read.
 */
data class LiveDisplayState(val promotionOff: Boolean, val samsung: Boolean) {
    val any get() = promotionOff || samsung
    companion object {
        fun of(context: Context) = LiveDisplayState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !NotificationManagerCompat.from(context).canPostPromotedNotifications(),
            Build.MANUFACTURER.equals("samsung", ignoreCase = true))
        /** The app's 실시간 알림 switch (Android 16+), else its notification settings. */
        fun promotionSettings(context: Context): Intent =
            Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        /** No public intent opens One UI's Now Bar list or the lock screen settings, so the system settings with the path in the sheet. */
        val nowBarSettings get() = Intent(Settings.ACTION_SETTINGS)
    }
}

/**
 * The 실시간 표시 안내 sheet: per [state], the 실시간 알림 step and, on Samsung, the Now Bar path, each with `설정 열기`; [close] is `나중에` from
 * `행사 안내 시작` (not asked again) or `닫기` from 설정 › 알림 `실시간 표시 설정` ([closeLabel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LiveDisplaySheet(state: LiveDisplayState, openPromotion: () -> Unit, openNowBar: () -> Unit, closeLabel: String, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("행사 안내를 실시간으로 보기", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (state.promotionOff) Step("잠금 화면·상태 표시줄에 실시간으로 보이려면 '실시간 알림'을 켜 주세요.", openPromotion)
            if (state.samsung) Step("Now Bar에 띄우려면 설정 › 잠금화면 및 AOD › Now bar에서 Dearby를 켜 주세요. 목록에 없으면 이 기기에서는 알림으로만 보여요.", openNowBar)
            if (!state.any) Text("실시간 알림이 켜져 있어요.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(close) { Text(closeLabel) } }
        }
    }
}

@Composable private fun Step(text: String, open: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        TextButton(open) { Text("설정 열기") }
    }
}
