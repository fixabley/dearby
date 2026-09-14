# iOS FSD 구조

[이슈 #1](https://github.com/fixabley/dearby/issues/1)과 [공통 설계 PR #3](https://github.com/fixabley/dearby/pull/3)의 FSD 책임·의존 방향을 단일 SwiftUI 앱에 적용한다.
공고 카드, 즐겨찾기 조직 카드, 상세 라우팅을 기능별 후속 커밋으로 분리한다. 기존 화면·문구·제스처·탭·sheet/back·ID·저장 키/배열·샘플을 유지한다.
외형 정비 [#2](https://github.com/fixabley/dearby/issues/2), 외부 DI/상태관리 라이브러리, 별도 빌드 모듈과 네트워크/API는 포함하지 않는다.

## 실제 디렉터리 트리

```text
apps/ios/
├── Dearby.xcodeproj/                     # 기존 synchronized Dearby 그룹·공유 scheme
├── Dearby/
│   ├── App/
│   │   ├── DearbyApp.swift               # 실제 의존성·단일 상태 소유
│   │   └── ContentView.swift             # 로딩·탭·페이지 목적지 조립
│   ├── Pages/
│   │   ├── Discovery/UI/DiscoveryView.swift
│   │   ├── Favorites/UI/FavoriteListView.swift
│   │   └── NoticeDetail/UI/NoticeDetailView.swift
│   ├── Widgets/
│   │   ├── ActivityCard/UI/ActivityCard.swift
│   │   └── FavoriteOrganizationCard/UI/FavoriteOrganizationCard.swift
│   ├── Features/
│   │   └── FavoriteOrganization/
│   │       ├── Model/FavoriteOrganizations.swift
│   │       ├── Model/SaveOrganizationResult.swift
│   │       └── API/
│   │           ├── FavoriteOrganizationsRepository.swift
│   │           └── UserDefaultsFavoriteOrganizationsRepository.swift
│   ├── Entities/
│   │   └── ActivityCatalog/
│   │       ├── Model/ActivityCatalog.swift
│   │       ├── Model/ActivityNoticeSummary.swift
│   │       ├── API/
│   │       │   ├── ActivityCatalogRepository.swift
│   │       │   └── BundleActivityCatalogRepository.swift
│   │       └── UI/NoticeClassificationView.swift
│   ├── Resources/activity-samples.json
│   └── Assets.xcassets/
├── tests/
│   ├── FavoritesStoreTests.swift
│   └── check_fsd_boundaries.py
├── ARCHITECTURE.md
└── README.md
```

`UI`, `Model`, `API`는 표현·도메인/상태·외부 데이터 접근 목적을 구분하는 segment다.
공고·조직·출처·계층·학교 맥락은 서로 연결된 **단일 ActivityCatalog entity slice**에 둔다. 서로 다른 entity로 억지 분리해 순환 참조를 만들지 않는다.
현재 여러 도메인에서 공유하는 범용 Swift UI가 없어 Shared 폴더를 만들지 않았다. 카드 전용 보조 표시는 widget 내부 private 타입이다.
향후 실제 공용 범용 표시·테마 코드가 필요하면 slice 없이 `Shared/UI` 등 목적별 segment에 둔다. 기존 asset catalog와 번들 리소스의 경로는 유지한다.
`Dearby/`의 Xcode synchronized 그룹이 모든 파일을 포함한다. 테스트와 `build/` 증거는 앱 타깃 밖에 둔다.

## 슬라이스 public API 계약

아래는 **슬라이스 밖에서 사용하는 네이티브 진입점**이다. Swift의 `public` 키워드나 JS barrel 파일을 의미하지 않는다.
단일 앱 모듈에서 진입점은 기본 `internal`이며 외부 모듈 API를 추가하지 않는다.

| 슬라이스 | 외부 진입점 | 입력·책임 |
| --- | --- | --- |
| Pages/Discovery | `DiscoveryView<Destination>` | 카탈로그·ID 집합·저장 콜백·App의 목적지 ViewBuilder; 로컬 sheet 선택·피드백 |
| Pages/Favorites | `FavoriteListView<Destination>` | 카탈로그·ID 집합·삭제 콜백·목적지 ViewBuilder; 목록·빈 상태 |
| Pages/NoticeDetail | `NoticeDetailView` | 공고·카탈로그; 상세 표시만 수행 |
| Widgets/ActivityCard | `ActivityCard` | `ActivityNoticeSummary`·저장 여부·position·compact·onSave/onShowDetail |
| Widgets/FavoriteOrganizationCard | `FavoriteOrganizationCard<Destination>` | 조직·카탈로그·삭제 콜백·목적지 ViewBuilder; 연결 공고의 기존 NavigationLink |
| Features/FavoriteOrganization | `FavoriteOrganizations`, `SaveOrganizationResult`, `FavoriteOrganizationsRepository`, `UserDefaultsFavoriteOrganizationsRepository` | 상태와 저장 계약; 구체 저장 구현은 App 조립 또는 독립 테스트에서 사용 |
| Entities/ActivityCatalog | `ActivityNoticeSummary`, `ActivityCatalog` 및 같은 Model 파일의 `ActivityNotice`, `ActivityOrganization`, `ActivitySource`, `ActivityField`, `ActivityIssue`, `ActivityContext`, `ActivitySchedule`; `ActivityCatalogRepository`, `BundleActivityCatalogRepository`; `NoticeClassificationView` | 순수 모델/조회, 교체 가능한 공급, 카드·즐겨찾기의 분류 표시 |

`NoticeFact`는 ActivityCard 파일의 private 구현, `NoticeIdentityView`는 NoticeDetail 파일의 private 구현이다.
Preview 저장소도 App 파일의 private 타입이다. 외부 소비자는 이 helper들을 직접 사용하지 않는다.
별도 빌드 모듈이 없으므로 폴더만으로 internal 진입점 접근을 컴파일러가 강제 차단하지는 않는다. 경계는 아래 검사와 리뷰로 보완한다.

## 의존 방향과 라우팅

허용 방향은 `App → Pages → Widgets → Features → Entities → Shared`이며 아래 레이어를 건너뛰어 참조할 수 있다.
같은 레이어의 다른 slice 참조와 상향 참조는 금지한다. App·Shared는 slice 없는 예외다.

현재 실제 흐름:

```text
App → Discovery page → ActivityCard widget → ActivityCatalog entity
App → Favorites page → FavoriteOrganizationCard widget → ActivityCatalog entity
App → NoticeDetail page → ActivityCatalog entity
App → FavoriteOrganization state → 같은 slice의 저장 protocol → UserDefaults 구현
App → ActivityCatalog 공급 protocol → 같은 entity의 번들 구현
```

ContentView가 Discovery와 Favorites에 `(ActivityNotice) -> Destination` ViewBuilder를 주입하며 그 안에서만 NoticeDetailView를 생성한다.
Discovery는 기존 `.sheet(item:)`의 로컬 선택값을 유지하고 주입된 목적지를 표시한다. Favorites의 widget은 기존 NavigationLink에 주입된 목적지를 연결한다.
페이지와 카드는 다른 페이지 타입을 모르며 공고 카드와 조직 카드도 서로 참조하지 않는다. 새로운 전역 라우터·선택 상태·AnyView 계층을 만들지 않는다.
Widget은 표시 데이터와 콜백만 받고 저장소·공유 상태를 직접 생성하거나 읽지 않는다. Entity UI 역시 데이터 공급을 실행하지 않는다.

## 상태 소유와 생명주기

| 상태 | 소유·수명 | 변경 흐름 |
| --- | --- | --- |
| 즐겨찾기 ID 집합 | DearbyApp의 단일 `@State`가 Feature의 `@Observable FavoriteOrganizations`를 앱 수명 동안 유지 | 화면 콜백 → 같은 MainActor 상태 → 저장 계약 → 루트 관찰 → 두 페이지에 최신 ID 값 |
| 카탈로그·로드 오류 | ContentView 로컬 `@State` | 기존 `.task`·다시 시도에서 주입된 동기 공급자를 호출 |
| 발견 상세 선택·피드백·햅틱 카운트 | DiscoveryView 로컬 `@State` | 저장/상세 콜백의 UI 결과만 관리 |
| 탭·내비게이션·스크롤 | 기존 SwiftUI 기본 컴포넌트 수명 | 기존 TabView/NavigationStack/ScrollView·sheet 동작 유지 |

ContentView의 `favorites.ids` 읽기는 **lazy Tab/NavigationStack 클로저 앞**에 유지한다. 내부 지연 클로저에서만 읽으면 페이지에 오래된 값이 남을 수 있다.
Feature 상태는 저장소를 주입받아 생성 시 복원하고, UI는 별도 상태 사본을 소유하지 않는다. 외부 프로세스의 저장 변경을 실시간 감시하지 않는다.
UserDefaults의 `dearby.favoriteOrganizationIDs.v1` 키, 정렬된 문자열 배열, 중복 방지·멱등 추가·명시적 삭제를 유지한다.
기업 ID·대회 프로그램 ID를 저장하고 상위 교육원·행사 학교를 자동 저장하지 않는다. 기존 운영부서/현재 모델에 없는 ID도 임의 삭제·변환하지 않는다.
[활동 규격](../../docs/product/activity-data-v1.md)과 [관심 대상 규칙](../../docs/product/interest-target-rules.md)을 따른다.

## 구조 검사와 한계

루트에서 `python3 apps/ios/tests/check_fsd_boundaries.py`를 실행한다.
Swift 타입 선언과 식별자 참조를 수집해 실제 상향 참조·동일 레이어 다른 slice 참조를 찾으며, UI의 Feature 상태/API 및 UserDefaults/Bundle 직접 접근도 거절한다.
대표 금지 참조(다른 페이지, 상위 페이지, 형제 widget, Feature 상태, UserDefaults, entity UI의 공급자, entity에서 widget)를 주입한 음성 fixture가 매번 실행된다.
`--slice`는 특정 컴포넌트를 점검하는 선택 인수이고, 최종 검증은 모든 Swift 파일을 검사한다.

이 검사는 Swift parser나 컴파일러 모듈 격리가 아닌 작은 lexical guard다. 주석·일반 문자열을 제외하므로 문자열 보간 내부 참조, 추론된 타입의 멤버 호출, 복잡한 alias·동적 호출·매크로는 놓칠 수 있고 같은 이름의 타입은 오탐할 수 있다.
추가 API를 만들 때 문서 진입점을 갱신하고 전체 구조 검사·빌드·실제 UI 흐름을 함께 리뷰한다.

## 새 기능 배치 예시

- 공고 카드만의 표시나 버튼은 `Widgets/ActivityCard/UI`와 해당 검사에 둔다. 그 페이지의 필터 선택은 `Pages/Discovery/UI` 로컬 상태로 둔다.
- 즐겨찾기 조직 카드의 표시 변경은 `Widgets/FavoriteOrganizationCard/UI`, 저장·삭제 행동과 저장 구현은 `Features/FavoriteOrganization/Model|API`에 둔다.
- 새 상세 화면은 Pages의 독립 slice에 만들고 **App에서** 목적지를 주입한다. Discovery/Favorites가 새 페이지를 직접 생성하지 않는다.
- 새로운 카탈로그 조회는 `Entities/ActivityCatalog/Model`, 실제 공급 교체는 같은 slice의 API와 App 조립을 변경한다. 네트워크 도입 시 async·취소·오류 정책은 해당 작업에서 정하며 지금 미리 구현하지 않는다.
- 서로 다른 widget이 필요한 도메인 분류 표시는 entity UI에, 실제 도메인 없는 공용 UI가 필요해지면 Shared/UI에 둔다. 빈 segment나 사용하지 않는 추상화를 추가하지 않는다.
- 공통 계약 변경은 coordinator와 먼저 조율한다. 각 기능 변경에 필요한 코드·검사·문서를 한 커밋에 묶으며 이미 게시된 커밋은 재작성하지 않는다.

## 검증 기록

2026-09-14 **이전 FSD 변경(4e9d9b2까지)**에서 실행한 결과다. 재현 명령은 [README](README.md)를 따른다.

| 검사 | 이번 결과 |
| --- | --- |
| FSD 구조 | 전체 14 Swift 파일 통과; 상향/형제 slice/상태·저장 접근 금지 음성 fixture 통과 |
| 독립 Swift 6 | 인메모리 추가·중복·삭제, 두 Observation 소비자의 추가/삭제 통지·동일 값, UserDefaults 기존 배열 호환·복원, 카탈로그 공급 교체·손상 오류 통과 |
| 컴포넌트별 빌드 | 공고 카드·즐겨찾기 카드·최종 상세 라우팅 모두 Xcode 26.6 Simulator Debug 빌드 통과, 최종 앱 실행 확인 |
| Discovery 라우팅 | 전용 기기에서 공고 버튼 → 상세 sheet 및 취소로 원래 카드 복귀, 기업/학교 분리 표시 확인 |
| Favorites 라우팅 | 조직 카드의 DB 공고 → 상세 NavigationLink 및 back → 목록 복귀 확인 |
| 카드·공유 상태 | KRC 카드 더블클릭 저장 → 두 기업 목록, KRC 삭제 → 발견 카드 미저장 표시, 앱 종료·재실행 후 DB만 유지 확인 |
| 카드 이동 | 접근성 scroll down으로 1/4 KRC → 2/4 DB 확인; 실제 터치 스와이프는 이번에도 검증하지 않음 |

전용 `Dearby-Issue1-iOS` (`A434888F-4096-48AE-91B2-37A498233B55`, iOS 26.5)을 사용했다. 사용자 기기를 초기화하거나 즐겨찾기를 삭제하지 않았다.
로컬 증거는 `build/fsd-regression/`의 `discovery-sheet.png`, `favorites-detail.png`, `restored-favorites.png` 및 검사 로그에 남긴다(커밋 제외).
이전 aa4a258의 공통 npm 13건·샘플 일치 및 화면 검증은 이전 기록이다. 이번에는 바뀐 Swift 구조·상태·라우팅 흐름을 중심으로 검사한다.
실제 터치 스와이프, VoiceOver 전체 흐름, 최대 글자 크기, iPad·가로·실기기 전체 검증은 남아 있다. 이번 도구 회귀는 상시 XCUITest가 아니다.

## 카드 입력 보완 — 이번 확인

`catalog.summary(for: notice)`가 notice·해결된 organization·실제 String contextNames를 묶는 순수 `ActivityNoticeSummary`를 만든다.
카드는 전체 카탈로그나 범용 data를 받지 않는다. position/saved/compact와 onSave/onShowDetail은 외부 표현 상태·이벤트로 유지한다.
미확정/알 수 없는 대상은 organization이 nil이며 원래 공고와 행사 학교 맥락은 보존한다. 모델에 저장·UI 부수효과를 추가하지 않는다.
이번 카드 요약 변경에서 독립 Swift의 해결/미확정·대회 요약 검사와 기존 상태/복원 검사, FSD 전체 15파일 검사, Simulator Debug 빌드를 실행해 통과했다.

## 저장 업무 연산·Repository 보완 — 이번 확인

`FavoriteOrganizations.saveOrganization(for:in:)`가 카탈로그에서 관심 대상 조직을 해결하고 저장한다.
`.saved(ActivityOrganization)`는 카탈로그의 정식 조직 이름을 반환하고 `.unresolved`는 상태·관찰 알림·저장 쓰기를 발생시키지 않는다.
Discovery는 App이 주입한 `(ActivityNotice) -> SaveOrganizationResult`의 결과를 기존 문구와 성공 햅틱 카운트로 변환한다.
App이 카탈로그와 단일 상태를 연결하며 별도 Service/UseCase·상태 사본을 만들지 않는다.

`ActivityCatalogRepository.load()`와 `BundleActivityCatalogRepository`는 기존 동기 카탈로그 공급 계약/구현의 이름을 명확히 한 것이다.
`FavoriteOrganizationsRepository.load()/save(_:)`와 `UserDefaultsFavoriteOrganizationsRepository` 역시 기존 저장 계약/구현이며 중복 wrapper가 아니다.
반복 저장도 같은 Set을 동기로 쓰고, 삭제·정렬 문자열 배열·기존 알 수 없는 ID 복원 의미를 유지한다.

구조 검사는 Pages에서 정확한 `Features/FavoriteOrganization/Model/SaveOrganizationResult.swift`의 결과 값 타입만 허용한다.
Page의 관찰 상태/Repository 접근, Widget의 결과 타입/상태 접근은 음성 fixture로 계속 금지한다.
이는 Pages → Features의 순수 결과 값 계약이며 컴파일러 접근 격리를 주장하지 않는다.

이번 최종 보완에서 README의 독립 swiftc 명령과 전체 16파일 구조 검사를 실행해 통과했다.
유효 조직·정식 이름 결과·반복 저장, nil/미등록 대상의 무쓰기·무변경·무알림, 두 소비자 Observation, 실제 UserDefaults 복원 및 카탈로그/요약 검사를 포함한다.
XcodeBuildMCP `build_sim`과 `build_run_sim`을 전용 기기/기존 scheme/Debug/`CODE_SIGNING_ALLOWED=NO`로 실행해 경고·오류 없이 통과했다.
빌드 로그는 `build_sim_2026-09-14T09-25-04-477Z_pid15343_80416ede.log`, 실행 빌드는 `build_run_sim_2026-09-14T09-25-18-892Z_pid15343_460c9de6.log`다.

전용 Dearby-Issue1-iOS에서 KRC 저장 버튼 → `한국농어촌공사 저장됨`, 카드 더블클릭 반복 → 두 기업 목록에 중복 없음,
즐겨찾기 탭 반영 → KRC 삭제 → 발견 미저장 표시를 확인했다. 새로 추가한 KRC만 삭제하여 기존 DB 즐겨찾기를 보존했다.
UI 도구의 첫 삭제는 오래된 접근성 인덱스로 거절되어 새 snapshot으로 재시도 후 확인했다.
로컬 증거는 `build/refinement-regression/`의 JSON과 `saved-feedback.png`, `shared-favorites.png`다(커밋 제외).
이번 callback 보완은 sheet/back 구조를 바꾸지 않아 해당 결과는 위 이전 회귀 기록을 구분해 유지한다.
실제 터치 스와이프·햅틱의 물리 감각·전체 접근성/실기기 회귀는 이번에도 미검증이다.
