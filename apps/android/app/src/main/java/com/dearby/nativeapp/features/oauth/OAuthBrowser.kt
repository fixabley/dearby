package com.dearby.nativeapp.features.oauth

import android.content.Context
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens a sign-in provider's page (Apple, Google) in a Custom Tab. [context] must be the Activity. Throws `ActivityNotFoundException` without a browser. */
class OAuthBrowser(private val context: Context) {
    fun open(url: String) = CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
}
