# Android FSD 구조와 상태 관리

[이슈 #1](https://github.com/fixabley/dearby/issues/1)의 사용자 요청과 [공통 설계 #3](https://github.com/fixabley/dearby/pull/3)을 적용한다. 기존 화면·문구·제스처·하단 Material 3 NavigationBar·sheet/back 및 저장 형식을 유지한다. 외형 정비 [#2](https://github.com/fixabley/dearby/issues/2), 네트워크·새 상태관리/DI 라이브러리·빌드 모듈 추가는 제외한다.

## 실제 디렉터리 트리

하나의 `:app` Gradle 모듈이다. 아래 경로는 `app/src/main/java/io/fixabley/dearby/` 기준이다.

```text
MainActivity.kt                         기존 manifest 컴포넌트, App 진입·의존성 조립
app/
  OpenVenueMap.kt                       geo Intent 생성·실행/실패 콜백
  DearbyApp.kt                          탭·상세 라우팅·공급/재시도·상태 전달
pages/
  discovery/ui/DiscoveryScreen.kt       피드·pager·피드백·공고 카드 조합
  favorites/ui/FavoritesScreen.kt       조직 목록·빈 상태·조직 카드 조합
  noticedetail/ui/
    NoticeDetailSheet.kt                상세 시트·dismiss/원문 콜백
    NoticeIdentity.kt                   상세 내부의 관심 조직·분류·맥락·회차
    NoticeLocationSection.kt            장소 요약·복수 장소 액션·층/호실 안내
    VenueMapButton.kt                   단일 장소 이름·지도 콜백
widgets/
  activitycard/ui/ActivityCard.kt        공고 내용·더블탭·저장·상세 열기
  favoriteorganizationcard/ui/
    FavoriteOrganizationCard.kt        조직·상위 경로·연결 공고·명시적 삭제
features/
  favoriteorganization/
    model/FavoritesState.kt             관찰 상태·멱등 추가·삭제
    api/FavoriteStore.kt                 저장 계약
    api/SharedPreferencesFavoriteStore.kt 기존 로컬 저장 구현
entities/
  activitycatalog/
    model/ActivityCatalog.kt            Organization/NoticeContext/Notice/ActivityCatalog
    model/ActivityLocation.kt           장소 summary/mode/status와 복수 venues
    model/ActivityVenue.kt              phase/name/address·선택 좌표
    model/VenueCoordinates.kt           유한 수·위경도 범위 검증
    api/DecodeActivityLocation.kt       선택 좌표 안전 해석 (slice 내부)
    api/CatalogProvider.kt              교체 가능한 공급 계약
    api/AssetCatalogProvider.kt         기존 JSON/출처 URL 매핑·번들 로딩
    ui/ActivityClassification.kt        활동 분류·행사 학교 표시
shared/
  ui/NoticeFact.kt                      도메인 없는 제목/값 표시
  ui/theme/Theme.kt                     기존 앱 테마
```

공고·조직·계층·맥락·출처 매핑은 서로 연결된 activitycatalog slice에 둔다. 필요 없는 Source 추상 타입이나 서로 참조하는 entity slice를 만들지 않는다. 빈 세그먼트나 사용하지 않는 추상화도 추가하지 않는다.

## 슬라이스 public API 계약

여기서 public API는 **slice 밖에서 사용할 네이티브 진입점**이라는 뜻이다. JavaScript barrel 파일을 복제하지 않는다. 아래 타입/Composable은 대부분 Kotlin `internal`로 모듈 밖 노출을 제한한다. `internal`이 같은 앱 모듈 안의 slice 접근을 막지는 않는다.

| 레이어 / slice | 외부 진입점 (slice 기준 경로·심볼) |
| --- | --- |
| App (segment 예외) | manifest의 `MainActivity`, `app/DearbyApp` 조립 |
| Pages / discovery | `ui.DiscoveryScreen` |
| Pages / favorites | `ui.FavoritesScreen` |
| Pages / noticedetail | `ui.NoticeDetailSheet`; `NoticeIdentity`, `NoticeLocationSection`, `VenueMapButton`은 slice 내부 helper |
| Widgets / activitycard | `ui.ActivityCard` |
| Widgets / favoriteorganizationcard | `ui.FavoriteOrganizationCard` |
| Features / favoriteorganization | `model.FavoritesState`, `api.FavoriteStore`, `api.SharedPreferencesFavoriteStore` |
| Entities / activitycatalog | `model.Organization`, `model.NoticeContext`, `model.Notice`, `model.ActivityCatalog`, `model.ActivityLocation`, `model.ActivityVenue`, `model.VenueCoordinates`, `api.CatalogProvider`, `api.AssetCatalogProvider`, `ui.ActivityClassification` |
| Shared (segment 예외) | `ui.NoticeFact`, `ui.theme.DearbyTheme` |

저장소 필드·상태 setter·내부 update 함수·JSON 배열 helper는 private이다. `NoticeIdentity`는 별도 Kotlin 파일에서 같은 상세 slice가 사용하는 internal helper이며 App 등 외부 slice에서 import하지 않도록 구조 검사로 제한한다. MainActivity의 루트 패키지는 기존 Android 컴포넌트 이름을 바꾸지 않기 위한 App 진입점 예외다.

## 의존 방향과 라우팅

```text
App → Pages → Widgets → Features → Entities → Shared
```

더 아래 레이어로 건너뛰는 참조는 허용한다. 같은 레이어의 다른 slice와 상위 레이어는 참조하지 않는다. App과 Shared는 slice 없이 목적별 segment를 두는 예외다.

`MainActivity`가 실제 CatalogProvider, FavoriteStore, FavoritesState를 생성한다. `DearbyApp`이 공급자를 호출하고 선택 탭과 상세 Notice를 소유한다. 발견/즐겨찾기 페이지는 상세 목적지의 타입을 모르며 `showDetail(Notice)` 콜백을 App에 전달한다. App만 NoticeDetailSheet를 생성하고 dismiss와 외부 URL 콜백을 연결한다. 페이지가 다른 페이지를 import하지 않으며 새 라우터 라이브러리를 도입하지 않는다.

공고 카드와 조직 카드는 서로 참조하지 않는다. 공고 카드는 공고·조직·학교 문자열·저장 여부·위치·저장/상세 콜백을 받는다. 조직 카드는 조직·상위 조직 목록·연결 공고·공고 ID별 학교 문자열·삭제/상세 콜백을 받는다. 공유 상태·저장소·공급자·Context에 직접 접근하지 않는다. 두 위젯의 활동 분류 표시는 Entities의 ActivityClassification을 사용한다.

## 상태 소유와 호환성

`MainActivity.onCreate`가 `FavoritesState(SharedPreferencesFavoriteStore(...))`를 한 번 만들어 루트에 전달한다. 재구성과 탭 전환은 같은 인스턴스를 사용한다. Activity/프로세스가 재생성되면 같은 로컬 저장소에서 새 상태가 복원된다. UI는 루트가 읽은 ID 집합과 save/remove 콜백만 받는다. Compose `mutableStateOf`가 추가·삭제를 관찰시키며 이벤트는 UI 스레드에서 실행한다. 소비자는 받은 Set을 변경하지 않는다.

| 임시 상태 | 소유와 기존 수명 |
| --- | --- |
| 선택 탭 | App의 rememberSaveable; Activity 재생성 시 복원 |
| 상세 선택 | App의 remember; dismiss/Activity 재생성 시 해제 |
| 공급 결과·재시도 | App의 remember(catalogProvider, retry); 탭 전환으로 재로딩하지 않음 |
| 카드 위치 | 발견 페이지의 기존 rememberPagerState |
| 저장 피드백 | 발견 페이지의 remember; 페이지에서 벗어나면 폐기 |

Features는 사용자 행동인 조직 저장·삭제와 저장 경계를 소유한다. 기존 파일 `dearby.favorites.v1`, 키 `organizationIDs`, StringSet 및 apply 쓰기 형식은 유지한다. 저장은 조직 ID의 멱등 추가이고 삭제는 명시적 동작이다. 기존 운영부서·알 수 없는 저장 ID를 임의로 치환하거나 삭제하지 않는다. 학교 맥락·분류·부모·관심 대상은 별개이며 부모/학교를 자동 저장하지 않는다.

Entities의 CatalogProvider는 현재 동기식 번들 공급용이다. 공급 교체는 App 주입으로 가능하지만 미래 비동기 API·인증·페이지네이션·취소 정책은 별도로 설계해야 한다. 모델은 Android/Compose를 참조하지 않는 순수 데이터·조회 계산이며 연결된 출처 URL 해석은 같은 slice의 api에 있다.

제품 의미는 [활동 규격](../../docs/product/activity-data-v1.md), [관심 대상 규칙](../../docs/product/interest-target-rules.md)을 따른다.

## 새 기능 배치 예시

- 카드 내용/저장 버튼 표현은 `widgets/activitycard/ui/`, 조직 카드의 연결 공고 표현은 `widgets/favoriteorganizationcard/ui/`에 둔다. 이벤트는 인수로 전달한다.
- 피드 필터의 화면 임시 선택은 `pages/discovery/ui/`에 둔다. 다른 페이지로 이동하는 목적지 콜백은 App에서 연결한다.
- 새로운 저장 행동은 `features/favoriteorganization/model/`, 저장 방식 교체는 같은 slice의 api와 App 조립에 둔다. 저장키 변경은 별도 호환 검토가 필요하다.
- 공고 분류 표시나 카탈로그 조회 계산은 `entities/activitycatalog/ui/` 또는 model에 둔다. source/organization의 서로 연결된 모델을 임의 cross-slice로 쪼개지 않는다.
- 도메인과 관계없는 제목/값 표시·테마만 Shared에 둔다. 한 번 쓰는 상세 helper를 범용이라는 이유로 Shared에 올리지 않는다.
- 실제 새 slice가 필요하면 문서의 진입점 표와 `scripts/check-fsd.py`의 API 목록도 함께 갱신한다.

## 구조 검사와 한계

앱 디렉터리에서 `python3 scripts/check-fsd.py --self-test`를 실행한다. main Kotlin 소스의 패키지/경로 일치, 상향 참조, 동일 레이어의 다른 slice, 문서에 없는 진입점, Pages/Widgets의 상태·공급·저장소/Context 참조를 검사한다. import(별칭 포함)와 완전한 패키지 이름 참조를 읽는다. App/Shared segment 및 기존 MainActivity 진입점 예외는 코드에 명시했다.

self-test는 메모리의 Kotlin 소스 사례 11개를 실제 검사 함수에 넣는다. 상향/페이지 간/위젯 간/상태 직접 참조/Android 저장/비공개 helper/완전한 이름 참조 등 금지 8건이 거절되고 허용 3건이 통과해야 한다.

이것은 간단한 정규식 기반 검사와 리뷰 규칙이며 compiler-enforced slice가 아니다. 테스트 소스·생성 소스·reflection·문자열/interpolation·동적 호출이나 Kotlin 전체 타입 해석은 검사하지 않는다. 주석·문자열을 단순히 제거하므로 복잡한 언어 구문은 누락/오탐할 수 있다. 같은 slice 안의 간접 경로까지 증명하지 않으며 import 없는 Swift 타입 참조를 분석하는 도구도 아니다. 같은 패키지 내부 접근이나 타입 별칭의 의미 분석은 컴파일러·리뷰로 보완한다.

## 실행과 검증

앱 디렉터리, Android Studio JDK 25, SDK 36 기준이다. local.properties는 Git에 넣지 않는다.

```sh
python3 scripts/check-fsd.py --self-test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
```

JVM 테스트는 `app/src/test/java/io/fixabley/dearby/features/favoriteorganization/model/FavoritesStateTest.kt`의 5건이다. 임시 저장소로 추가·중복·삭제·기존 ID 복원과 여러 소비자의 Compose 관찰 일관성을 앱 없이 검증한다.

계측은 기존 DiscoveryFlowTest/LocalDataTest/CatalogSupplyTest 7건의 참조를 새 경계로 갱신하고, 독립 ActivityCardTest 1건·FavoriteOrganizationCardTest 2건·상세 back 시 같은 카드/탭 복귀 1건을 추가했다. 총 11건이며 조직 카드에서 상세를 닫은 뒤 즐겨찾기 탭 유지도 확인한다. 실제 SharedPreferences XML 복원과 번들 해석·교체 공급 재시도를 포함한다. runner 1.7.0/Espresso 3.7.0은 유지한다.

`DiscoveryFlowTest`는 테스트 기기의 즐겨찾기를 초기화하므로 사용자 emulator-5554에서는 실행하지 않는다. 이번 전용 AVD는 checkout의 `build/avd/Dearby_Architecture_Test.avd`를 emulator-5556으로 실행했다. 구조 검사는 테스트 소스를 제외하지만 JVM/계측 컴파일과 실행은 전체 테스트를 검증한다.

## 커밋과 검증 기록

기존 게시 커밋 3c8d2c5는 보존한다. FSD 후속 변경은 공통 의존 경계(카드들이 함께 쓰는 이유로 선행), 공고 카드, 즐겨찾기 조직 카드, 상세/라우팅별로 관련 코드·테스트·문서를 함께 묶었다.

2026-09-14 이번 실행: 공통 경계 이동 후 JVM5/Debug/계측APK 통과, 공고 카드 독립 계측1건과 조직 카드 독립 계측2건 통과. 최종 구조 검사 및 전체 회귀 결과는 아래에 기록한다. 이전 구조의 JVM5/계측7/cold-start smoke는 3c8d2c5의 과거 결과이며 이번 실행으로 주장하지 않는다.

TalkBack·최대 글자 크기·태블릿·가로 화면 전체 검증, 비동기 공급·네트워크·기기 간 동기화는 이번 범위 밖이다.

최종 FSD 검증(2026-09-14): 구조검사 main Kotlin 17개 파일 통과, self-test 금지 8건 거절/허용 3건 통과. JVM 상태5건·Debug 빌드·전용 API36.1 계측11건 통과(실패/오류/skip 0), Lint 오류0/권고11건. 발견→상세→back 시 같은 카드/탭, 즐겨찾기→상세→back 시 같은 탭, 카드의 독립 이벤트·기존 저장 복원까지 현재 코드로 확인했다. 한국어 문자열 집합과 샘플 JSON은 3c8d2c5와 동일하다.

로컬 증거는 `build/fsd-foundation.log`, `build/fsd-activitycard.log`, `build/fsd-favoritecard.log`, `build/fsd-final.log`, `app/build/test-results/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.html`에 있다(Git 제외). 구조 검사 명령은 추가 설치 없이 Python 표준 라이브러리만 사용한다. 이전 cold-start smoke는 이번에 반복하지 않았고 실제 저장/Activity 재생성 회귀는 이번 계측에 포함했다.

## 장소 좌표와 지도 액션 (2026-09-14 추가)

기존 화면 보존에 대한 위 FSD 기록은 지도 액션 추가 전의 결과다. 이번에는 사용자 승인으로 상세 활동 장소에 유효한 좌표를 가진 각 venue의 지도 버튼과 “층·호실은 장소 안내를 확인해 주세요.” 안내를 추가한다. 카드의 기존 장소 문구는 `location.summary` 그대로다. Android의 기존 단순 표시 문자열은 이미 String이므로 추가 래퍼 제거는 없다.

`Notice.location`은 `ActivityLocation`이다. mode/status/summary와 venue별 phase/name/address/선택 좌표를 유지한다. 좌표 누락·null·부분 값·문자열·범위 밖 값은 unknown(null)으로 읽고 0으로 대체하지 않는다. 명시적인 (0,0)은 유효하다. JSON schemaVersion 1.0.0과 canonical evidence/coordinateEvidence/eligibility 구조는 그대로 보존하며 앱용 표시 모델만 필요한 필드를 읽는다. decoder는 activitycatalog 내부 helper이고 새 model 타입 3개는 slice 외부 진입점이다.

상세 slice의 `NoticeLocationSection`과 `VenueMapButton`은 각자 파일에 둔 internal helper다. 입력 데이터/콜백만 받고 Context·공급자·저장소·공유 상태에 접근하지 않는다. 온라인 장소는 좌표가 있어도 지도 버튼을 제공하지 않는다. App이 `(ActivityVenue) -> Unit`을 상세에 주입하고 MainActivity가 `openVenueMap`을 연결한다. 좌표 검증을 실행 직전에도 적용하고 [Android 지도 Intent 규격](https://developer.android.com/guide/components/intents-common#Maps)에 따라 ACTION_VIEW geo URI와 인코딩한 좌표/장소 이름을 전달한다. package/component를 고정하지 않아 OS가 설치된 앱 선택을 처리한다. ActivityNotFoundException/SecurityException은 native Toast 안내로 처리한다. 웹 fallback·권한·현재 위치·길찾기·앱 내 geocoding/네트워크 호출은 추가하지 않는다.

새 장소 표시 기능은 같은 상세 ui slice에, 좌표 해석은 entity api에, 외부 앱 실행은 App에 배치한다. 지도 앱 내부의 화면과 경로는 앱이 제어하지 않는다. 공통 계약/검증 좌표는 [별도 PR #6](https://github.com/fixabley/dearby/pull/6)에서 관리하며 이 플랫폼 PR은 Android 리소스만 갱신한다. 건물 대표 좌표이며 출입구·층·호실 정밀 좌표가 아니다. 리소스 SHA-256: `407b0c5ed29d066ae9cf2c7d146749f1566e38ba966369db6cfd5ec1952feb6f`. 이 브랜치의 shared snapshot은 #6 통합 전 버전이므로 비교는 coordinator가 제공한 canonical 원본과 수행한다.

새 테스트: JVM VenueCoordinatesTest는 0/경계/비유한 수/범위를 검증한다. 계측 ActivityLocationDecodeTest는 이전 JSON·null/부분/비정상 좌표·복수 장소·구조 보존을, VenueMapIntentTest는 한국어/&/# 이름 인코딩·unpinned 요청·미설치/차단 피드백을 검증한다. VenueMapFlowTest는 복수 장소 버튼/온라인 제외/명시적 클릭 및 발견→상세→App 요청 전달을 검사한다. 기존 11건 UI/저장 회귀도 함께 실행한다.

이번 지도 검증 결과: 구조24파일/self-test11, JVM7건, Debug 빌드, Lint 오류0/권고11, 전용 emulator-5556 계측20건 통과(실패/오류/skip 0). 첫 계측의 비유한 JSON 기대 1건은 Android JSONObject가 Infinity를 파싱 단계에서 거부하는 실제 동작에 맞춰 수정하고 전체20건을 재실행했다. 비유한 수를 가진 직접 모델은 JVM/Intent 단계에서 거부되며 비유한 JSON은 기존 App 공급 실패·재시도 화면으로 처리된다. `cmd package resolve-activity --brief -a android.intent.action.VIEW -d 'geo:36.62819644470018,127.45787581357385?q=36.62819644470018,127.45787581357385'`로 전용 기기의 MapsActivity 설치를 확인했다. 실제 지도 렌더링·다중 앱 chooser 화면·TalkBack 전체 점검은 수행하지 않았으며 실행 요청/오류는 주입한 함수로 캡처했다. 사용자5554는 조작하지 않았다.

로그: `build/maps-build-verified.log`, `build/maps-instrumentation.log`, 기존 표준 JVM/계측 XML 및 Lint 보고서 (Git 제외). 위 FSD17/JVM5/계측11 기록은 과거 실행이고 이번 결과는 24/7/20이다.
