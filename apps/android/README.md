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
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
Android 12 이상 에뮬레이터 또는 USB 디버깅 기기를 선택해 Android Studio에서 Run하거나,
연결된 기기에 `./gradlew :app:installDebug`로 설치합니다.

Release 빌드는 R8 코드·리소스 축소를 사용합니다. 배포용 서명 설정은 아직 없으며,
서명 키는 저장소에 커밋하지 않습니다.

JVM 단위 테스트는 앱 실행 없이 임시 저장소로 즐겨찾기 추가·중복·삭제·복원과 여러 소비자의 상태 일관성을 검증합니다.
전용 에뮬레이터를 지정해 `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`를 실행하면 더블탭,
세로 넘김, 조직 중복 방지, Activity 재생성 후 저장 유지·삭제, 버튼·탭·연결 상세와 실제 저장/카탈로그 공급을 검증합니다.
`emulator-5556`은 예시이며 실제 전용 기기 serial을 확인합니다. 사용자 즐겨찾기가 있는 `emulator-5554`에는 실행하지 않습니다.
테스트는 실행 기기의 Dearby 즐겨찾기를 초기화하므로 개발용 기기를 사용합니다.

## 구조

- `app/src/main/java/io/fixabley/dearby/MainActivity.kt`: 앱 진입점
- `app/src/main/java/io/fixabley/dearby/shared/ui/theme/Theme.kt`: 라이트·다크 테마
- `app/src/main/java/io/fixabley/dearby/app/`: 루트 화면 조합과 상태 전달
- `app/src/main/java/io/fixabley/dearby/feature/`: discovery·favorites·noticedetail 화면과 기능별 UI
- `app/src/main/java/io/fixabley/dearby/entities/activitycatalog/`: 응집된 카탈로그 모델과 공급 경계
- `app/src/main/java/io/fixabley/dearby/features/favoriteorganization/`: 즐겨찾기 행동·관찰 상태·저장 경계
- `app/src/main/java/io/fixabley/dearby/shared/ui/`: 범용 표시와 테마
- `app/src/test/`: 앱 없는 JVM 상태 테스트
- `app/src/androidTest/`: Compose 흐름과 실제 로컬 데이터 계측 테스트
- `app/src/main/assets/activity-samples.json`: 공통 기준 파일에서 복사한 샘플
- `app/src/main/res/`: 문자열, 시작 테마, 임시 런처 아이콘
- `gradle/libs.versions.toml`: 플러그인과 라이브러리 버전
- `gradle/wrapper/`, `gradlew`, `gradlew.bat`: 재현 가능한 Gradle 실행 환경

실제 디렉터리 트리, 상태 생명주기, 의존 방향, 새 기능 배치 예시는 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고합니다.

AGP의 내장 Kotlin 지원을 사용하므로 `org.jetbrains.kotlin.android` 플러그인은 적용하지 않습니다.
참고: [AGP 내장 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[AGP 9.1 호환성](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

## 공고 분류와 조직 관계

카드에는 활동 분류와 행사 관련 학교를, 상세에는 관심 조직·상위 조직·활동 분류·
역할별 관련 기관·회차를 표시합니다. 즐겨찾기는 조직별 연결 공고 수와 각 공고의 분류·학교를 보여줍니다.
학교 맥락은 조직의 부모와 구분하며, 관심 표시는 공고에 지정된 기업·프로그램만 저장합니다.
기존에 저장한 조직의 연결 공고가 없어도 항목을 유지하고 빈 상태를 안내합니다.
전체 조직 트리를 접고 펼치는 탐색 화면은 아직 포함하지 않습니다.
