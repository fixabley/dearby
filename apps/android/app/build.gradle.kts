plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Contract "연결 설정": origins come from the environment (or same-named Gradle properties), never code.
fun connection(name: String) = providers.environmentVariable(name).orElse(providers.gradleProperty(name))
    .orNull?.takeIf { it.isNotBlank() }
val apiOrigin = connection("DEARBY_API_ORIGIN")
// Debug falls back to the API dev server on the emulator host.
val debugApiOrigin = apiOrigin ?: "http://10.0.2.2:3000"

android {
    namespace = "com.dearby.nativeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dearby.nativeapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_ORIGIN", "\"$debugApiOrigin\"")
        }
        release {
            buildConfigField("String", "API_ORIGIN", "\"${apiOrigin.orEmpty()}\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// Release needs https://<domain> without IP, port, path or upper case; checked only when a release build runs.
val validateReleaseOrigins by tasks.registering {
    val origins = mapOf("DEARBY_API_ORIGIN" to apiOrigin)
    doLast {
        val domain = Regex("^https://([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]([a-z0-9-]*[a-z0-9])?$")
        for ((name, value) in origins) if (value == null || !domain.matches(value))
            throw GradleException("$name must be https://<domain> without IP, port or path for release builds (got '${value.orEmpty()}').")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseOrigins) }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
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
    // 패스키 로그인(계약 "패스키 로그인"): Credential Manager, and Play services for Android 13 and lower.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    // 애플 로그인(계약 "애플 로그인", 웹 방식): Apple's page opens in a Custom Tab (Apache-2.0).
    implementation("androidx.browser:browser:1.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
}
