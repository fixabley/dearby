# Dearby Android

Native first slice for #38. Kotlin / Compose, API 26+, compile/target 36. No bundled accounts, login bypass, seeded people, or delivery simulation. The default API URL is empty and requests report configuration errors.

## Build

Use JDK 17+ (local verification uses Android Studio JBR 21), Android SDK platform 36 / build-tools 36.0.0. Set `ANDROID_HOME` or local `local.properties`.

```sh
python3 scripts/check-fsd.py --self-test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Configure an origin **without `/v1`** via `-PdearbyApiUrl=https://your-authorized-server.example`. No production hostname is assumed. Emulator development may use `-PdearbyApiUrl=http://10.0.2.2:PORT`; HTTP is allowed only in debug and release transport rejects HTTP. Session tokens are Keystore encrypted, backups disabled. Do not put tokens in Gradle properties.

The shared protocol is `shared/contracts/native-v1.md`. QR temporarily encodes `dearby://card/UUID?label=...` and is app-only; public HTTPS/App Links and cross-platform scanning are blocked in #44/#43. Photos and external camera results are decoded with ZXing; this is not a continuous camera scanner. The camera preview is lower resolution and photo selection can be used when recognition fails.

## Persistence and safety

The logged-out profile tab requires email login; internal recoverable local drafts are not exposed as guest profile creation. Room stores local profile drafts, account-keyed profile cache, public card display cache, and guest card IDs. Guest IDs are only inserted after a successful public lookup; later import/network errors preserve existing IDs. Duplicate scans retain the first saved context/date. A local draft is not silently uploaded during sign-in. Profile edits do not mutate published cards. Selection defaults empty, import defaults empty, successful imports remove only selected confirmed IDs. Account logout waits for server revocation before clearing local session. The one active mutation gate serializes login/logout/profile/import/send to prevent stale cross-account results.

Five tabs are present. Discovery/saved activity integration, push, calendar, external application forms, registered activity selection, production email receipt, real two-device exchange, and production links are explicitly unfinished; #41/#42/#44 track them.

## Dependency evidence

`docs/evidence/dependency-registry.json` records Google Maven metadata fetched 2026-09-27 KST, including newer available stable versions. AGP 9.1.1 + Gradle 9.3.1 + built-in Kotlin 2.2.10 match the [official compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes) and installed SDK. [Built-in Kotlin guidance](https://developer.android.com/build/migrate-to-built-in-kotlin) avoids applying a duplicate Kotlin Android plugin. Room 2.8.5 uses KSP 2.3.12; [Room release docs](https://developer.android.com/jetpack/androidx/releases/room), [KSP release](https://github.com/google/ksp/releases/tag/2.3.12). [Compose BOM map](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle), [ZXing](https://github.com/zxing/zxing/releases) were checked.

These are available stable pins, not claims of newest releases: existing local compiler/SDK-compatible Compose 2026.02.01, Activity 1.12.4 and Lifecycle 2.10.0 keep the first slice reproducible; a newer BOM/toolchain upgrade needs its own SDK/behavior verification. No prerelease was selected.

UI instrumentation explicitly pins Espresso 3.7.0: the transitive older version failed on API36 with InputManager.getInstance; [official AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test) document the replacement with getSystemService.
