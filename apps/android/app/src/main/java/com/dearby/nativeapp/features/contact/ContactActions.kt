package com.dearby.nativeapp.features.contact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object ContactActions {
    fun open(context: Context, state: ContactActionState): String? {
        // Revalidate at the effect boundary rather than trusting a UI-supplied target.
        val target = contactAction(state.id, state.kind, state.label, state.value).target
        if (target == null) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(state.label, state.value))
            return "${state.label} 값을 복사했습니다."
        }
        val action = when { target.startsWith("tel:") -> Intent.ACTION_DIAL; target.startsWith("mailto:") -> Intent.ACTION_SENDTO; else -> Intent.ACTION_VIEW }
        context.startActivity(Intent(action, Uri.parse(target)))
        return null
    }
}
