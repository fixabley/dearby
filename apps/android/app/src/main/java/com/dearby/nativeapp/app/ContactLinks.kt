package com.dearby.nativeapp.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/** Phone and email open the phone or mail app; links open only when they are https. */
fun openContact(context: Context, contact: ContactState) {
    val target = when (contact.kind) {
        "phone" -> "tel:" + contact.value.filter { it.isDigit() || it == '+' }
        "email" -> "mailto:" + contact.value
        else -> contact.value.takeIf { it.startsWith("https://") }
    }
    target?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) } }
}
