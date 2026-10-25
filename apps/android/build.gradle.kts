plugins {
    id("com.android.application") version "9.3.3" apply false
    // AGP built-in Kotlin carries KGP 2.2.10; this raises it (2026-10-25: Firebase BoM 35 ships Kotlin 2.4 metadata). Not applied to :app.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // 새 모집 푸시(contract "즐겨찾기(하트)·새 모집 푸시 알림"): applied by :app only when app/google-services.json is present.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
