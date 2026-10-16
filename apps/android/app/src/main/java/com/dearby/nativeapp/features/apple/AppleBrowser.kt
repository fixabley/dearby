package com.dearby.nativeapp.features.apple

import android.content.Context
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens Apple's sign-in page in a Custom Tab. [context] must be the Activity. Throws `ActivityNotFoundException` without a browser. */
class AppleBrowser(private val context: Context) {
    fun open(url: String) = CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
}
