package com.dearby.nativeapp.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/** The app shell's Snackbar (`DearbyApp`), for one-line notices from any screen. */
val LocalNotice = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * 연락처 바로가기 (2026-10-26 "연락처 아이콘·바로가기"): phone opens the dialer, the rest their address (`ContactState.target`); a 카카오톡 ID is copied
 * with "ID를 복사했어요" on the shell's Snackbar below Android 13 (13+ shows the system's copy confirmation).
 */
@Composable fun rememberOpenContact(): (ContactState) -> Unit {
    val context = LocalContext.current
    val notice = LocalNotice.current
    return remember(context, notice) { { contact -> openContact(context, contact, notice) } }
}

private fun openContact(context: Context, contact: ContactState, notice: (String) -> Unit) {
    contact.target?.let { target ->
        runCatching { context.startActivity(Intent(if (target.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, target.toUri())) }
        return
    }
    contact.copyText?.let { id ->
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("카카오톡 ID", id))
        // Android 13+ shows its own copy confirmation; a second one would repeat it (contract: 스낵바 또는 시스템 복사 알림).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notice("ID를 복사했어요")
    }
}
