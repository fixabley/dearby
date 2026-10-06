import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Contract "연결 설정": origins come from the environment (or same-named Gradle properties), never code.
fun connection(name: String) = providers.environmentVariable(name).orElse(providers.gradleProperty(name))
    .orNull?.takeIf { it.isNotBlank() }
val apiOrigin = connection("DEARBY_API_ORIGIN")
val webOrigin = connection("DEARBY_WEB_ORIGIN")
// Debug falls back to the API and web dev servers on the emulator host.
val debugApiOrigin = apiOrigin ?: "http://10.0.2.2:3000"
val debugWebOrigin = webOrigin ?: "http://10.0.2.2:3210"
fun host(origin: String?) = runCatching { URI(origin!!).host }.getOrNull() ?: "invalid.invalid"

android {
    namespace = "com.dearby.nativeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dearby.nativeapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_ORIGIN", "\"$debugApiOrigin\"")
            buildConfigField("String", "WEB_ORIGIN", "\"$debugWebOrigin\"")
            manifestPlaceholders["dearbyWebHost"] = host(debugWebOrigin)
        }
        release {
            buildConfigField("String", "API_ORIGIN", "\"${apiOrigin.orEmpty()}\"")
            buildConfigField("String", "WEB_ORIGIN", "\"${webOrigin.orEmpty()}\"")
            manifestPlaceholders["dearbyWebHost"] = host(webOrigin)
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// Release needs https://<domain> without IP, port, path or upper case; checked only when a release build runs.
val validateReleaseOrigins by tasks.registering {
    val origins = mapOf("DEARBY_API_ORIGIN" to apiOrigin, "DEARBY_WEB_ORIGIN" to webOrigin)
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Compose transitively selects older Espresso without the Android 16 input fix.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
