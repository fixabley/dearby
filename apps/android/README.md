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
python3 scripts/check-fsd.py --self-test
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
흐름 테스트는 기존 즐겨찾기를 백업/복원하지만 실행 중 값을 변경하므로 전용 개발 기기만 사용합니다.

## 구조와 사용 흐름

공고는 `entities/notice/NoticeModel`, 조직은 `entities/organization/OrganizationModel`로 독립 관리합니다(실제 파일은 각 model segment). App의 NoticeSession이 두 lazy cache-aside 저장소와 카드/상세 ViewModel 수명을 공유합니다. ViewModel이 조직 ID를 해석해 immutable State를 만들고 UI는 State와 콜백만 받습니다. 즐겨찾기는 기존 FavoritesState 하나를 관찰하여 다른 탭에서 삭제해도 기존 카드의 saved가 갱신됩니다.

- `app/`, `app/data/`: 앱 조립·라우팅·OS 어댑터·번들 transport
- `pages/discovery/ui/`, `pages/favorites/ui/`: State 목록 표시
- `pages/noticedetail/model/`, `ui/`: NoticeDetailViewModel → NoticeDetailState → 상세 시트
- `widgets/noticecard/model/`, `ui/`: NoticeCardViewModel → NoticeCardState → 카드
- `widgets/favoriteorganizationcard/model/`, `ui/`: 조직 카드의 표시 조합과 삭제 콜백
- `entities/notice/`, `entities/organization/`: 독립 model/api, 서로 참조하지 않음
- `features/favoriteorganization/`, `features/addtocalendar/`: 공유 저장 상태·순수 캘린더 초안
- `shared/ui/`: 범용 표시·테마

실제 트리·진입점·캐시 교체/관찰 수명·새 기능 배치·검사 한계는 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고합니다. 앱 안에 원시/상세 공고 Entity를 중복 보관하지 않으며 조직 이름/경로는 State의 표시값입니다. 공고에는 선택 조직 및 명시적 맥락 역할의 ID만 있습니다. 출처/필드별 근거도 실제 디코딩합니다. 기존 summary는 검토 요약이며 새 AI 생성이라고 표시하지 않습니다.

공고를 세로로 넘기고 더블탭/버튼으로 조직을 저장합니다. 즐겨찾기는 연결 공고와 분류·학교를 표시하며 관심 대상·부모·학교 역할을 구분합니다. 상세의 지도 버튼은 유효한 좌표가 있는 각 장소에만 표시합니다. 신청/활동 단계의 캘린더 버튼은 알려진 날짜로 OS 편집기를 열며 메모에는 검증된 원본 URL 하나만 전달합니다. 원본이 없거나 유효하지 않으면 메모는 비우며 신청/온라인 URL로 대체하지 않습니다. 온라인 단계에 다른 단계의 오프라인 장소를 붙이지 않습니다. 사용자가 편집·저장/취소하며 앱은 저장 완료로 간주하지 않습니다. 권한/현재 위치/직접 일정 저장/외부 라이브러리는 추가하지 않습니다.

기존 JSON `activities`·asset `activity-samples.json`·ID·저장 키·test tag·Android Activity 이름은 호환성을 위해 유지합니다. 공통 계약은 [PR #6](https://github.com/fixabley/dearby/pull/6), 작업은 [이슈 #1](https://github.com/fixabley/dearby/issues/1)과 [설계 #3](https://github.com/fixabley/dearby/pull/3)을 참조합니다.

## 이번 검증 (2026-09-14)

구조49파일/self-test21, JVM29, Debug·계측 APK 컴파일, Lint 오류0/경고12, 전용5556 계측30 통과(실패/오류/skip0). 두 독립 캐시·같은 VM의 snapshot 교체·외부 컴포넌트 즐겨찾기 삭제의 Compose 관찰·출처/단계 장소·기존 카드/탭/상세/저장·지도/캘린더 전달을 확인했습니다. 추가로 `./gradlew :app:assembleDebugAndroidTest`로 기기 테스트 참조를 컴파일할 수 있습니다.

이번 로그는 `build/state-final-build.log`, `build/state-instrumentation.log`입니다. 과거 단계 결과는 Git/로컬 인계 이력에 남기며 최신 구조의 결과와 구분합니다. 사용자5554를 조작하지 않았고 전용5556만 종료했습니다. 외부 지도/캘린더 앱 내부 UI·실제 일정 저장은 이번에 검증하지 않았습니다. 네트워크·로그인·원문 자동 추출은 미구현이며 동기식 번들/메모리 캐시입니다.


## 캘린더 메모 변경 검증 (2026-09-14)

신청/활동 일정 모두 메모는 검증된 원본 HTTP(S) URL 문자열 하나이며 누락/오류는 빈 문자열이다. 신청/온라인 URL로 대체하지 않는다. 이번 JVM30·Debug·계측 APK 컴파일·FSD49파일/self-test21 통과, 증거는 `build/calendar-source-only.log`와 표준 JVM XML이다. 앞선 계측30/Lint 결과는 이전 구조 작업 결과이며 이번에는 기기 실행·일정 저장·Lint를 반복하지 않았다.
