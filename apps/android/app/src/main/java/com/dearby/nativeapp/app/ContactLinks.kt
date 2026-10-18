package com.dearby.nativeapp.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/** Phone and email open the phone or mail app; links open only when they are https (`ContactState.target`). */
fun openContact(context: Context, contact: ContactState) {
    contact.target?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) } }
}
