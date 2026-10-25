plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // 새 모집 푸시(contract "즐겨찾기(하트)·새 모집 푸시 알림"): applied by :app only when app/google-services.json is present.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
