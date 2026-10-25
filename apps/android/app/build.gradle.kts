import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// 새 모집 푸시 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25): Firebase Cloud Messaging needs the Firebase project's
// `app/google-services.json`, which lives outside the repository (~/.dearby-deploy, ignored here) and is copied in for builds that should
// receive pushes. Without it the plugin is not applied, Firebase never initialises and the app runs without push (CI, local builds).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
// Contract "연결 설정": origins come from the environment (or same-named Gradle properties), never code.
fun connection(name: String) = providers.environmentVariable(name).orElse(providers.gradleProperty(name))
    .orNull?.takeIf { it.isNotBlank() }
val apiOrigin = connection("DEARBY_API_ORIGIN")
// Where the public card pages live; the 내 명함 QR encodes `<web origin>/cards/<id>` (contract "명함 교환"). Its host is also the App Links host
// (contract "유니버설 링크·App Links·활동 공유·오픈그래프"), so the manifest filter and the link parser read the same origin.
val webOrigin = connection("DEARBY_WEB_ORIGIN") ?: "https://dearby.wid.io.kr"
// Google sign-in (contract "구글 로그인 — 2026-10-19 네이티브"): the web OAuth client id the ID token is issued for. Empty hides the button.
val googleServerClientId = connection("GOOGLE_SERVER_CLIENT_ID") ?: ""
// 맞춤 대기열 끝 광고 (2026-10-25 user decision, AdMob native, non-personalised). Debug always uses Google's published test ids so no real ad is
// ever served or clicked in development. Release needs both ids; with either missing the manifest still gets the test app id (the SDK's init
// provider throws at launch without one) while the unit id stays empty, so the app never initialises or requests ads and skips the ad scene.
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdMobNativeUnitId = "ca-app-pub-3940256099942544/2247696110"
val adMob = listOf("ANDROID_ADMOB_APP_ID", "ANDROID_ADMOB_NATIVE_UNIT_ID").map(::connection).takeIf { ids -> ids.all { it != null } }?.map { it!! }
// Debug falls back to the API dev server on the emulator host.
val debugApiOrigin = apiOrigin ?: "http://10.0.2.2:3000"
// Play upload key (2026-10-19): keystore and passwords live outside the repository (env or same-named Gradle properties).
// Only when all four are present is the release build signed; otherwise it stays unsigned, which is enough for lint/CI.
val uploadSigning = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith(::connection).takeIf { values -> values.values.all { it != null } }?.mapValues { it.value!! }

android {
    namespace = "com.dearby.nativeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.wid.dearby" // 2026-10-18 사용자 결정: iOS 출시 번들 ID와 같게. 코드 패키지(namespace)는 그대로
        minSdk = 26
        targetSdk = 36
        // Play release CI (android-play-release.yml) passes a run-based code above the manual first upload (1).
        versionCode = connection("ANDROID_VERSION_CODE")?.toInt() ?: 1
        versionName = "0.1.0"
        manifestPlaceholders["webHost"] = URI(webOrigin).host
    }
    uploadSigning?.let { keys ->
        signingConfigs.create("upload") {
            storeFile = file(keys.getValue("ANDROID_KEYSTORE_PATH"))
            storePassword = keys.getValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = keys.getValue("ANDROID_KEY_ALIAS")
            keyPassword = keys.getValue("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_ORIGIN", "\"$debugApiOrigin\"")
            buildConfigField("String", "WEB_ORIGIN", "\"$webOrigin\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
            manifestPlaceholders["adMobAppId"] = testAdMobAppId
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"$testAdMobNativeUnitId\"")
        }
        release {
            uploadSigning?.let { signingConfig = signingConfigs.getByName("upload") }
            buildConfigField("String", "API_ORIGIN", "\"${apiOrigin.orEmpty()}\"")
            buildConfigField("String", "WEB_ORIGIN", "\"$webOrigin\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
            manifestPlaceholders["adMobAppId"] = adMob?.get(0) ?: testAdMobAppId
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"${adMob?.get(1).orEmpty()}\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// Release needs https://<domain> without IP, port, path or upper case; checked only when a release build runs.
val validateReleaseOrigins = tasks.register("validateReleaseOrigins") {
    val origins = mapOf("DEARBY_API_ORIGIN" to apiOrigin, "DEARBY_WEB_ORIGIN" to webOrigin)
    doLast {
        val domain = Regex("^https://([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]([a-z0-9-]*[a-z0-9])?$")
        for ((name, value) in origins) if (value == null || !domain.matches(value))
            throw GradleException("$name must be https://<domain> without IP, port or path for release builds (got '${value.orEmpty()}').")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseOrigins) }
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        // `TopAppBarScrollBehavior` (shared/ui/DearbyTopBar, 2026-10-20) is an experimental Material 3 type that every caller would otherwise have to opt into.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    // Launch splash (logo on the app background) on every Android version.
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 활동 이미지(`imageUrl`) 표시 (Apache-2.0).
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    // 계정 API 전송(계약 "설정"의 PATCH 포함): Android HttpURLConnection은 PATCH를 거부하므로 OkHttp(Coil이 이미 끌어오는 4.12.0)를 직접 쓴다 (Apache-2.0).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 패스키 로그인(계약 "패스키 로그인"): Credential Manager, and Play services for Android 13 and lower.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    // 구글 로그인(계약 "구글 로그인 — 2026-10-19 네이티브"): Sign in with Google through Credential Manager (Apache-2.0).
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // 애플 로그인(계약 "애플 로그인", 웹 방식): Apple's page opens in a Custom Tab (Apache-2.0).
    implementation("androidx.browser:browser:1.10.0")
    // QR encoding only for 내 명함 QR 보이기 (contract "명함 교환"); no camera, no scanning.
    implementation("com.google.zxing:core:3.5.3")
    // 새 모집 푸시: FCM token and messages (Apache-2.0); inert, like Analytics below, until google-services.json configures Firebase.
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    // 사용 통계 (2026-10-25 user decision): Firebase Analytics, privacy-minimal (manifest: no ad id, no SSAID, no ad personalisation).
    implementation("com.google.firebase:firebase-analytics")
    // 맞춤 대기열 끝 광고 (2026-10-25): one AdMob native ad per queue view, requested non-personalised (features/ads).
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
}
