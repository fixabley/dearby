# Dearby Android

Kotlin + Jetpack Compose + Material 3 기반 네이티브 앱입니다.

| 항목 | 설정 |
| --- | --- |
| 앱 이름 | Dearby |
| Application ID / Namespace | `io.fixabley.dearby` |
| 최소 지원 | Android 12 (API 31) |
| Compile / Target SDK | 36 |
| 버전 | 1.0.0 (1) |
| Android Gradle Plugin / Gradle | 9.1.1 / 9.3.1 |
| Kotlin / Compose Compiler | 2.2.10 |
| Compose BOM | 2026.02.01 |

## 개발 환경

Android Studio에서 이 디렉터리(`apps/android`)를 엽니다.
SDK Manager에서 Android SDK Platform 36과 Build Tools 36.0.0을 설치합니다.
Gradle JDK는 Android Studio에 포함된 JDK를 사용합니다. 현재 프로젝트는 JDK 25에서
빌드를 검증하며, 앱 바이트코드는 Java 17을 대상으로 합니다.

SDK 경로는 Android Studio가 생성하는 `local.properties`에 설정하거나
`ANDROID_HOME` 환경 변수로 지정합니다. `local.properties`는 커밋하지 않습니다.

## 빌드와 검증

모노레포 루트에서 실행합니다.

```sh
cd apps/android
./gradlew :app:assembleDebug :app:lintDebug
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
Android 12 이상 에뮬레이터 또는 USB 디버깅 기기를 선택해 Android Studio에서 Run하거나,
연결된 기기에 `./gradlew :app:installDebug`로 설치합니다.

Release 빌드는 R8 코드·리소스 축소를 사용합니다. 배포용 서명 설정은 아직 없으며,
서명 키는 저장소에 커밋하지 않습니다. 테스트 타깃은 아직 추가하지 않았습니다.

## 구조

- `app/src/main/java/io/fixabley/dearby/MainActivity.kt`: 앱 진입점과 시작 화면, Compose Preview
- `app/src/main/java/io/fixabley/dearby/ui/theme/Theme.kt`: 라이트·다크 테마
- `app/src/main/res/`: 문자열, 시작 테마, 임시 런처 아이콘
- `gradle/libs.versions.toml`: 플러그인과 라이브러리 버전
- `gradle/wrapper/`, `gradlew`, `gradlew.bat`: 재현 가능한 Gradle 실행 환경

AGP의 내장 Kotlin 지원을 사용하므로 `org.jetbrains.kotlin.android` 플러그인은 적용하지 않습니다.
참고: [AGP 내장 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[AGP 9.1 호환성](https://developer.android.com/build/releases/agp-9-1-0-release-notes).
