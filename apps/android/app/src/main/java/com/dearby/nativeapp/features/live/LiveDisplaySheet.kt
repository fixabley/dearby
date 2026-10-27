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
 * ([NotificationManagerCompat.canPostPromotedNotifications]). One UI's Now Bar is not guided (2026-10-27 "안드로이드 나우바는 빼고").
 */
data class LiveDisplayState(val promotionOff: Boolean) {
    companion object {
        fun of(context: Context) = LiveDisplayState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !NotificationManagerCompat.from(context).canPostPromotedNotifications())
        /** The app's 실시간 알림 switch (Android 16+), else its notification settings. */
        fun promotionSettings(context: Context): Intent =
            Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}

/**
 * The 실시간 표시 안내 sheet: per [state], the 실시간 알림 step with `설정 열기`; [close] is `나중에` from
 * `행사 안내 시작` (not asked again) or `닫기` from 설정 › 알림 `실시간 표시 설정` ([closeLabel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LiveDisplaySheet(state: LiveDisplayState, openPromotion: () -> Unit, closeLabel: String, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("행사 안내를 실시간으로 보기", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (state.promotionOff) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("잠금 화면·상태 표시줄에 실시간으로 보이려면 '실시간 알림'을 켜 주세요.", style = MaterialTheme.typography.bodyMedium)
                TextButton(openPromotion) { Text("설정 열기") }
            } else Text("실시간 알림이 켜져 있어요.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(close) { Text(closeLabel) } }
        }
    }
}
