package com.dearby.nativeapp.shared.config

/**
 * 개인정보 동의 (contract "개인정보 동의", 2026-10-19): the document versions the app agrees to. They match the 시행일 of the web
 * `/privacy` and `/terms` pages; the server rejects any other value (400 CONSENT_VERSION) and asks again when its versions move on.
 */
object ConsentVersions {
    const val PRIVACY = "2026-10-19"
    const val TERMS = "2026-10-19"
    fun privacyUrl(webOrigin: String) = "$webOrigin/privacy"
    fun termsUrl(webOrigin: String) = "$webOrigin/terms"
}
