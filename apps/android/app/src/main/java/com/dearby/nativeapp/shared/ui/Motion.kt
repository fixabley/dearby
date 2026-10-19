package com.dearby.nativeapp.shared.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 시스템 애니메이션 배율이 0이면 동작 줄이기로 본다. */
@Composable fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
