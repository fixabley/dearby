# Android 구조와 상태 관리

[이슈 #1](https://github.com/fixabley/dearby/issues/1)의 승인 Seed v2(A~F)를 적용한다. 공통 책임 기준은 [설계 PR #3](https://github.com/fixabley/dearby/pull/3)에서 확인한다. 기존 화면·문구·제스처·하단 Material 3 NavigationBar와 로컬 저장 형식은 유지한다. 외형 정비는 별도 [이슈 #2](https://github.com/fixabley/dearby/issues/2)다.

## 실제 파일 배치

하나의 `:app` 모듈 안에서 Kotlin 패키지로 책임을 구분한다. 새 외부 상태 관리·DI 라이브러리는 없다.

```text
app/src/main/java/io/fixabley/dearby/
├── MainActivity.kt                         앱 진입·실제 의존성 생성·외부 링크 열기
├── app/
│   └── DearbyApp.kt                        루트 조합·탭·상세 선택·로딩/재시도
├── feature/
│   ├── discovery/
│   │   ├── DiscoveryScreen.kt              피드·pager·저장 피드백
│   │   └── NoticeCard.kt                   공고 카드·더블탭/저장/상세 이벤트
│   ├── favorites/
│   │   └── FavoritesScreen.kt              저장 조직·연결 공고·삭제·빈 상태
│   └── noticedetail/
│       ├── NoticeDetailSheet.kt            상세 시트·원문 링크 이벤트
│       └── NoticeIdentity.kt               관심 조직·상위 조직·분류·맥락·회차
├── core/
│   ├── model/
│   │   └── ActivityCatalog.kt              Organization/NoticeContext/Notice/ActivityCatalog
│   ├── state/
│   │   └── FavoritesState.kt               단일 관찰 상태·추가/삭제
│   ├── data/
│   │   ├── CatalogProvider.kt              카탈로그 공급 경계
│   │   ├── AssetCatalogProvider.kt         기존 번들 JSON 해석
│   │   ├── FavoriteStore.kt                ID 집합 읽기/쓰기 경계
│   │   └── SharedPreferencesFavoriteStore.kt 실제 로컬 저장
│   └── ui/
│       └── NoticeFact.kt                   카드와 상세가 공유하는 정보 표시
└── ui/theme/
    └── Theme.kt                           기존 앱 테마

app/src/test/java/io/fixabley/dearby/core/state/
└── FavoritesStateTest.kt                   앱 없는 JVM 상태 테스트
app/src/androidTest/java/io/fixabley/dearby/
├── DiscoveryFlowTest.kt                    기존 3건 + 버튼/양쪽 탭/연결 상세 회귀
├── LocalDataTest.kt                        실제 저장 및 번들 공급 검증
└── CatalogSupplyTest.kt                    공급자 교체·실패/재시도·루트 상태 유지
```

## 상태 소유와 생명주기

`MainActivity.onCreate`에서 `AssetCatalogProvider`와 `FavoritesState(SharedPreferencesFavoriteStore(...))`를 한 번 생성해 `DearbyApp`에 전달한다. 화면이나 카드가 저장소·공유 상태를 새로 만들지 않는다. 재구성은 같은 상태 인스턴스를 사용하고 탭 전환도 이를 바꾸지 않는다. Activity가 재생성되거나 앱 프로세스가 다시 시작되면 새 상태가 같은 저장소에서 복원된다. 별도 ViewModel·싱글턴·서비스 로케이터는 필요하지 않다.

`FavoritesState.ids`는 Compose `mutableStateOf`로 관찰하는 읽기 전용 프로퍼티다. 루트가 ID 집합을 읽고 탐색·즐겨찾기에 `favoriteIds`와 `onSave`/`onRemove`를 전달한다. 저장·삭제는 UI 스레드에서 호출하고 새 집합을 저장 경계에 넘긴 뒤 상태에 반영한다. 여러 소비자는 다음 재구성에서 같은 집합을 받는다. 소비자는 받은 Set을 직접 변경하지 않는다. 백그라운드 동시 쓰기나 다른 프로세스의 저장소 변경을 감시하는 기능은 없다.

| 임시 상태 | 소유 / 기존 복원 동작 |
| --- | --- |
| 선택 탭 | `DearbyApp`의 `rememberSaveable`; Activity 재생성 시 복원 |
| 상세 선택 | `DearbyApp`의 `remember`; 시트 닫기/Activity 재생성 시 해제 |
| 카탈로그 결과·재시도 횟수 | `DearbyApp`의 `remember(catalogProvider, retry)`; 탭 전환으로 다시 읽지 않음, 재시도나 새 Activity에서 로딩 |
| 카드 위치 | `DiscoveryScreen`의 기존 `rememberPagerState`; Compose 저장 상태 복원 규칙 유지 |
| 저장 피드백 | `DiscoveryScreen`의 `remember`; 화면에서 벗어나면 폐기 |

## 의존 방향과 데이터 경계

```text
MainActivity → 실제 CatalogProvider / FavoriteStore 구현 생성
MainActivity → FavoritesState → FavoriteStore
DearbyApp → CatalogProvider → ActivityCatalog
DearbyApp → feature 화면(모델·ID 집합·이벤트 콜백)
feature 화면 → core.model / core.ui
사용자 이벤트 → 루트 콜백 → FavoritesState → FavoriteStore → 새 상태 → 재구성
```

`core.model`은 Android·Compose·저장소를 참조하지 않는 모델과 순수 조회/표시 계산이다. `core.state`는 Compose 관찰 기능과 `FavoriteStore` 인터페이스만 사용해 Android Context 없이 JVM에서 시험할 수 있다. `core.data`의 실제 구현만 Assets/SharedPreferences를 읽는다. `feature`와 `core.ui`는 저장소·공급자·공유 상태 객체에 접근하지 않는다. 외부 URL 실행도 상세 UI가 문자열 콜백을 호출하고 앱 진입점이 처리한다.

현재 `CatalogProvider.load()`는 동기식이며 기존 작은 번들 샘플에 맞춘 경계다. 테스트에서는 람다 공급자로 바꾼다. 향후 API 공급은 데이터 구현과 루트의 비동기 로딩 정책을 함께 설계해야 하며 이번에 네트워크·인증·페이지네이션을 미리 구현하지 않는다.

## 저장 및 제품 의미 호환

- 실제 파일 `dearby.favorites.v1`, StringSet 키 `organizationIDs`, `apply()` 저장 형식을 유지한다.
- 저장은 조직 ID의 멱등 추가다. 반복 더블탭/버튼은 삭제하지 않으며 삭제는 목록의 명시적 동작이다.
- 기존 운영부서나 알 수 없는 ID도 상태·저장소에서 보존한다. 카탈로그에 없는 ID의 화면 표시 정책도 기존과 같다(목록은 카탈로그에서 찾은 조직을 표시).
- 학교 맥락, 조직 부모, 활동 분류, 관심 대상은 별개다. 기업 또는 지속 대회 프로그램만 지정 ID로 저장하며 학교·부모는 자동 추가하지 않는다.
- 샘플 JSON·조직 ID·분류/맥락/회차 해석은 바꾸지 않는다. 현재 연결 공고가 없는 기존 조직도 삭제 가능한 항목으로 남는다.

제품 의미는 [활동 규격](../../docs/product/activity-data-v1.md), [관심 대상 규칙](../../docs/product/interest-target-rules.md), [반복 01](../../docs/product/iteration-01.md)을 따른다.

## 새 기능 배치 예시

- 탐색 전용 카드나 표시 옵션은 `feature/discovery/`에 둔다. UI 인수는 모델·표시 값·콜백이다.
- 즐겨찾기 조직 행을 별도로 재사용해야 하면 `feature/favorites/OrganizationRow.kt`로 분리한다. 한 화면에서만 쓰는 UI를 먼저 공통 디렉터리로 올리지 않는다.
- 두 기능에서 실제로 함께 쓰는 정보 표시는 `core/ui/`에 두고 순수 조회 계산은 `core/model/`에 둔다.
- 여러 화면에 필요한 새 즐겨찾기 이벤트는 `FavoritesState`에 추가하고 루트가 같은 인스턴스의 콜백을 주입한다. 화면 내부에서 별도 상태 소유자를 만들지 않는다.
- 새 저장 방식은 `core/data/`의 `FavoriteStore` 구현과 `MainActivity` 조립을 변경한다. 기존 저장 키를 바꾸려면 별도 호환·마이그레이션 검토가 필요하다.
- 새 카탈로그 공급은 `core/data/`에 구현하고 루트에 주입한다. 새로운 기능 화면은 `feature/<기능>/`에 두고 `DearbyApp`에서 연결한다.

## 검증 방법과 범위

앱 디렉터리에서 실행한다. 환경과 APK 경로는 [README](README.md)를 따른다.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# 전용 테스트 기기를 지정한다. 사용자 emulator-5554에는 실행하지 않는다.
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
```

JVM 테스트는 임시 메모리 저장소로 추가·중복·삭제·복원·기존 ID 유지와 두 Compose 관찰 소비자의 일관성을 검증한다. Activity·에뮬레이터·Robolectric이 필요 없다. 계측은 기존 제스처/저장 3건에 버튼·탭·연결 상세 회귀를 추가하며, 실제 별도 SharedPreferences XML의 기존 키 복원·쓰기/삭제와 디스크 반영, 번들 디코딩, 교체 공급자의 재시도를 확인한다. 실제 저장 테스트의 객체 재생성과 XML 검사는 프로세스 종료 테스트와 구분한다.

`DiscoveryFlowTest`는 실제 앱 즐겨찾기를 초기화하므로 전용 개발 기기에서만 실행한다. `LocalDataTest`는 고유한 테스트 저장 파일을 만들고 정리한다. 기존 runner 1.7.0/Espresso 3.7.0을 유지한다. TalkBack·최대 글자 크기·태블릿·가로 화면 전체 검증과 외형 정비는 이번 범위에 포함하지 않는다.

## 이번 실행 결과 — 2026-09-14

Android Studio JDK 25와 SDK 36, 전용 `Dearby_Architecture_Test` API 36.1(`emulator-5556`)에서 확인했다. 사용자 `emulator-5554`는 테스트·설치·초기화·종료 대상으로 사용하지 않았다.

| 검사 | 결과 |
| --- | --- |
| `:app:testDebugUnitTest` | 5건 통과, 앱 실행 없음 |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | 성공 |
| `:app:lintDebug` | 오류 0, 권고 경고 11건(버전 업데이트·KTX·버전 카탈로그 사용) |
| `ANDROID_SERIAL=emulator-5556 :app:connectedDebugAndroidTest` | 7건 통과: 기존 3건 + 버튼/탭/연결 상세 1건 + 실제 데이터 2건 + 공급 교체/재시도 1건 |
| 별도 프로세스 재시작 확인 | 전용 기기에 기존 XML 형식의 `krc`, `cbnu-career`를 넣고 두 번 cold launch; 서로 다른 PID에서 KRC 저장 표시 및 두 ID 유지 확인 |
| 정적 회귀 확인 | 한국어 UI 문자열 집합 동일, Android 샘플이 공통 원본과 바이트 일치, 기능 UI의 저장소/상태 객체 의존 없음 |

처음 계측 실행은 새 상세 검사에서 배경 목록과 시트의 같은 기업명을 함께 찾아 1건 실패했다. 검사 범위를 상세 시트 하위로 좁힌 뒤 7건 모두 통과했으며 제품 UI 변경은 없었다. 과거 3건 기록을 이번 실행으로 대체해 주장한 것이 아니라 현재 하단 NavigationBar 상태에서 재실행한 결과다.

재시작 확인은 Gradle 계측 종료 후 Debug APK를 전용 기기에 다시 설치하고, 앱을 `am force-stop`한 상태에서 `run-as`로 `shared_prefs/dearby.favorites.v1.xml`에 기존 StringSet 형식을 넣어 수행했다. `am start -W -n io.fixabley.dearby/.MainActivity` → UIAutomator의 `저장됨 · 한국농어촌공사` 표시/저장 XML 확인 → `am force-stop`을 두 번 반복해 PID가 바뀐 것도 확인했다. 이는 계측 테스트의 객체 재생성 검사와 별도로 실행한 cold-start smoke 검사다.

로컬 산출물(빌드 폴더, Git 제외): JVM 결과 `app/build/test-results/testDebugUnitTest/`, 계측 결과 `app/build/outputs/androidTest-results/connected/debug/`, Lint `app/build/reports/lint-results-debug.html`, cold-start 증거 `build/cold-start-result.txt`와 `build/cold-start-1.xml`, `build/cold-start-2.xml`.
