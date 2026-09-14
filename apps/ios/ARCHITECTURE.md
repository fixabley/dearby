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
│   │   ├── ContentView.swift             # 로딩·탭·페이지 목적지 조립
│   │   ├── NoticeDetailDestination.swift # 상세 OS 동작·실패 알림 조립
│   │   ├── VenueMapLink.swift            # 순수 Apple Maps URL 생성
│   │   └── VenueMapLauncher.swift        # URL 열기 콜백·실패 처리
│   ├── Pages/
│   │   ├── Discovery/UI/DiscoveryView.swift
│   │   ├── Favorites/UI/FavoriteListView.swift
│   │   └── NoticeDetail/UI/
│   │       ├── NoticeDetailView.swift
│   │       ├── NoticeIdentityView.swift
│   │       ├── NoticeDetailField.swift
│   │       ├── NoticeIdentityFact.swift
│   │       ├── NoticeLocationView.swift
│   │       └── VenueMapButton.swift
│   ├── Widgets/
│   │   ├── ActivityCard/UI/ActivityCard.swift
│   │   ├── ActivityCard/UI/NoticeFact.swift
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
│   │       ├── Model/ActivityLocation.swift
│   │       ├── Model/ActivityVenue.swift
│   │       ├── Model/ActivityCoordinates.swift
│   │       ├── API/
│   │       │   ├── ActivityCatalogRepository.swift
│   │       │   └── BundleActivityCatalogRepository.swift
│   │       └── UI/NoticeClassificationView.swift
│   ├── Resources/activity-samples.json
│   └── Assets.xcassets/
├── tests/
│   ├── FavoritesStoreTests.swift
│   ├── check_fsd_boundaries.py
│   └── VenueMapTests.swift
├── ARCHITECTURE.md
└── README.md
```

`UI`, `Model`, `API`는 표현·도메인/상태·외부 데이터 접근 목적을 구분하는 segment다.
공고·조직·출처·계층·학교 맥락은 서로 연결된 **단일 ActivityCatalog entity slice**에 둔다. 서로 다른 entity로 억지 분리해 순환 참조를 만들지 않는다.
현재 여러 도메인에서 공유하는 범용 Swift UI가 없어 Shared 폴더를 만들지 않았다. 카드 전용 보조 표시는 같은 widget slice의 UI 파일에 둔다.
향후 실제 공용 범용 표시·테마 코드가 필요하면 slice 없이 `Shared/UI` 등 목적별 segment에 둔다. 기존 asset catalog와 번들 리소스의 경로는 유지한다.
`Dearby/`의 Xcode synchronized 그룹이 모든 파일을 포함한다. 테스트와 `build/` 증거는 앱 타깃 밖에 둔다.

## 슬라이스 public API 계약

아래는 **슬라이스 밖에서 사용하는 네이티브 진입점**이다. Swift의 `public` 키워드나 JS barrel 파일을 의미하지 않는다.
단일 앱 모듈에서 진입점은 기본 `internal`이며 외부 모듈 API를 추가하지 않는다.

| 슬라이스 | 외부 진입점 | 입력·책임 |
| --- | --- | --- |
| Pages/Discovery | `DiscoveryView<Destination>` | 카탈로그·ID 집합·저장 콜백·App의 목적지 ViewBuilder; 로컬 sheet 선택·피드백 |
| Pages/Favorites | `FavoriteListView<Destination>` | 카탈로그·ID 집합·삭제 콜백·목적지 ViewBuilder; 목록·빈 상태 |
| Pages/NoticeDetail | `NoticeDetailView` | 공고·카탈로그·typed onOpenMap 콜백; 상세 표시 |
| Widgets/ActivityCard | `ActivityCard` | `ActivityNoticeSummary`·저장 여부·position·compact·onSave/onShowDetail |
| Widgets/FavoriteOrganizationCard | `FavoriteOrganizationCard<Destination>` | 조직·카탈로그·삭제 콜백·목적지 ViewBuilder; 연결 공고의 기존 NavigationLink |
| Features/FavoriteOrganization | `FavoriteOrganizations`, `SaveOrganizationResult`, `FavoriteOrganizationsRepository`, `UserDefaultsFavoriteOrganizationsRepository` | 상태와 저장 계약; 구체 저장 구현은 App 조립 또는 독립 테스트에서 사용 |
| Entities/ActivityCatalog | `ActivityLocation`, `ActivityVenue`, `ActivityCoordinates`, `ActivityNoticeSummary`, `ActivityCatalog` 및 같은 Model 파일의 `ActivityNotice`, `ActivityOrganization`, `ActivitySource`, `ActivityContext`, `ActivitySchedule`; `ActivityCatalogRepository`, `BundleActivityCatalogRepository`; `NoticeClassificationView` | 순수 모델/조회, 교체 가능한 공급, 카드·즐겨찾기의 분류 표시 |

`NoticeFact(label:value:)`는 Widgets/ActivityCard/UI/NoticeFact.swift의 slice 내부 표시 helper다. 파일 간 사용을 위해 기본 internal이며 외부 slice 진입점으로 사용하지 않는다.
`NoticeIdentityView(notice:catalog:)`, `NoticeDetailField(title:value:)`, `NoticeIdentityFact(label:value:icon:)`는 Pages/NoticeDetail/UI의 개별 파일에 있는 slice 내부 표시 helper다.
파일 간 사용에 필요한 기본 internal만 사용하며 slice 외부에서는 NoticeDetailView를 진입점으로 사용한다.
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

ContentView가 Discovery와 Favorites에 `(ActivityNotice) -> Destination` ViewBuilder를 주입하며 그 안에서 App의 NoticeDetailDestination이 NoticeDetailView를 조립한다.
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

## UI 보조 컴포넌트 파일 분리

2026-09-14 카드의 NoticeFact를 같은 Widgets/ActivityCard/UI 파일로 이동했다. String label/value 및 기존 VStack·글꼴·행 제한을 그대로 유지하며 Shared로 승격하지 않는다.
카드 분리 시 전체 17 Swift 파일 구조 검사와 Simulator Debug 빌드가 통과했다(2026-09-14 09:38 UTC 빌드 로그).

상세의 함수형 detail/fact는 각각 NoticeDetailField/NoticeIdentityFact의 body로 옮기고 NoticeIdentityView도 별도 파일로 분리했다.
모든 입력·조건·ForEach ID·폰트·간격·접근성 식별자·modifier 순서를 유지했다. 보조 View는 자체 상태나 저장소를 갖지 않는다.

이번 전체 생산 UI 조사 목록(경로는 Dearby/ 기준):

| 조사한 파일 | 결과 |
| --- | --- |
| App/DearbyApp.swift | App 조립만 존재, 분리 없음 |
| App/ContentView.swift | body와 비 UI loadCatalog만 존재; PreviewFavoritesRepository는 UI가 아니므로 제외 |
| Pages/Discovery/UI/DiscoveryView.swift | body와 저장 결과 처리 함수만 존재, 분리 없음 |
| Pages/Favorites/UI/FavoriteListView.swift | body와 데이터 조회 속성만 존재, 분리 없음 |
| Pages/NoticeDetail/UI/NoticeDetailView.swift | NoticeIdentityView, detail, fact를 위 세 파일로 분리 |
| Widgets/ActivityCard/UI/ActivityCard.swift | NoticeFact를 같은 UI의 별도 파일로 분리 |
| Widgets/FavoriteOrganizationCard/UI/FavoriteOrganizationCard.swift | 별도 명명된 helper 없음; 작은 inline NavigationLink label 유지 |
| Entities/ActivityCatalog/UI/NoticeClassificationView.swift | 단일 View만 존재, 분리 없음 |

전체 생산 Swift 파일의 View 선언·some View 반환 함수 검색과 위 8개 원본 UI 파일 읽기로 조사했다.
분리 후 네 helper 파일도 확인했으며 추가 동거 UI 타입·함수형 UI helper는 없다. body, 작은 inline ViewBuilder, 비 UI 모델/저장/preview 구현은 이번 분리 대상이 아니다.

이번 최종 검증: 전체 20 Swift 파일 FSD 경계와 금지 fixture 통과, README의 독립 swiftc 명령 및 기존 저장/관찰/카탈로그/summary/무효 대상 테스트 모두 통과했다.
카드 분리와 상세 분리 각각 XcodeBuildMCP build_sim(Debug, CODE_SIGNING_ALLOWED=NO) 빌드가 경고·오류 없이 성공했다.
최종 로그는 build_sim_2026-09-14T09-39-06-779Z_pid15343_246d46a4.log다.
이번에는 상태 없는 표시 코드의 파일 이동과 함수→View 변환만 수행해 새 mirror 테스트나 런타임 재실행은 추가하지 않았다.
앞선 저장/라우팅 UI 회귀는 이전 기록이며 이번 결과로 표시하지 않는다. 개인/전용 시뮬레이터의 앱 데이터나 환경을 조작하지 않았다.

## 문자열 모델 정리
ActivityNotice의 audience/eligibility/application은 String, benefits/qualityIssues는 [String]이다. Decodable extension이 원본 객체의 summary만 읽으며 별도 DTO/문자열 wrapper를 만들지 않는다. 자동 memberwise 초기화는 테스트와 projection에서 유지한다. 원본 evidence/eligibility 등 구조화 JSON은 변경하지 않는다. 장소는 summary/mode/status/venues를 갖는 ActivityLocation과 ActivityVenue로 구분한다. 이번 기존 canonical JSON 전체 표시 문자열 일치·상태/복원 테스트 및 구조 검사와 Simulator 빌드를 실행했다.

## 장소 좌표·지도 링크

좌표 공통 계약은 [별도 PR #6](https://github.com/fixabley/dearby/pull/6)의 schemaVersion 1.0.0 선택 확장이다.
ActivityLocation(summary/mode/status/venues), ActivityVenue(phase/name/address/coordinates), ActivityCoordinates(latitude/longitude)는 순수 entity 값이며 UIKit/MapKit/저장 메서드가 없다.
좌표 누락·null·불완전·잘못된 타입·비유한 수·범위 초과는 해당 venue.coordinates만 nil로 처리하여 원본 장소와 공고를 유지한다.
0,0은 명시적으로 주어진 경우 유효하고 미상 기본값으로 생성하지 않는다. 좌표의 생성자와 Decodable 모두 검증하며 여러 장소의 순서를 보존한다.
원본 evidence/coordinateEvidence·자격 구조는 리소스 JSON에 그대로 보존하고 앱은 필요한 표시/좌표만 디코딩한다.

슬라이스 내부 helper 계약: NoticeLocationView(location:onOpenMap:)는 summary를 유지하고 venuesWithCoordinates만 VenueMapButton(venue:onOpenMap:)으로 표시한다.
새 UI는 각각 파일로 분리하며 callback은 ActivityVenue 값만 넘긴다. entity의 venuesWithCoordinates는 유효 좌표가 있는 장소 조회이고 지도 서비스/URL을 알지 못한다.
App/NoticeDetailDestination은 기존 sheet/NavigationLink 위치에서 상세와 실패 alert를 함께 조립하므로 오류 피드백이 열린 상세 위에 나타난다.
App/VenueMapLink.url(for:)는 URLComponents의 ll/q 항목으로 한글·&·# 장소 이름을 안전하게 인코딩한다.
[Apple 공식 Map Links](https://developer.apple.com/library/archive/featuredarticles/iPhoneURLScheme_Reference/MapLinks/MapLinks.html)의 좌표와 핀 이름 계약을 따른다.
App/VenueMapLauncher.open(_:using:onFailure:)는 유효한 URL만 주입된 OS opener에 전달한다. OS는 Maps 또는 웹 처리를 선택하고 실패 completion에는 native alert를 요청한다.
사용자가 버튼을 누르기 전 외부 동작이 없으며 위치 권한·현재 위치·길찾기·지오코딩·앱 네트워크/라이브러리/모듈을 추가하지 않는다.

최종 canonical 원본은 /Users/jominjun/Documents/dearby/shared/contracts/activities/sample.json이고 앱 리소스에만 복사했다.
SHA256 407b0c5ed29d066ae9cf2c7d146749f1566e38ba966369db6cfd5ec1952feb6f 일치 확인. 이 checkout의 shared/다른 플랫폼은 수정하지 않았다.
KRC/DB는 N12 도서관 건물, 멘토링은 N16-1 인문대 건물의 공통 담당 검증 좌표이며 대회 장소는 좌표 미상이다.
장소 summary의 층·호실은 유지하고 지도 옆에 `층·호실은 장소 안내를 확인해 주세요.`를 표시한다. 실내·입구 정밀 위치를 의미하지 않는다.

이번 검사: 좌표 누락/null/부분/타입 오류/NaN/Infinity/범위/0 및 경계값, 복수 장소의 버튼 대상 조회,
한글/&/# URL 왕복과 ll/q 요청, 성공/처리 불가/좌표 없음의 open/failure 콜백을 독립 테스트로 통과했다.
원래 좌표 없는 shared JSON과 새 앱 리소스로 기존 상태·표시·복원 검사도 각각 통과했다.
Xcode Simulator Debug 최종 빌드·실행 성공, build_run_sim_2026-09-14T10-16-22-184Z_pid15343_6cee89b5.log에 경고·오류 없음.
전용 Dearby-Issue1-iOS에서 KRC 상세의 장소/층 안내/지도 버튼을 확인하고 좌표 링크를 눌러 Apple Maps의 장소 이름·36.62820/127.45788 핀 표시까지 확인했다.
Maps 최초 알림 안내는 `지금 안 함`을 선택했다. 앱 위치 권한·경로 동작은 실행하지 않았고 개인 기기/즐겨찾기는 건드리지 않았다.
증거는 build/maps-regression/의 UI JSON 및 apple-maps-pin.png다. 접근성 인덱스가 바뀐 입력은 실제 화면의 버튼 위치를 확인해 재시도했다.
Maps 미설치/OS 거절의 실기기 UI는 미검증이며 실패 callback은 주입 테스트로 확인한다. 기존 터치 스와이프·물리 햅틱/전체 접근성 한계는 유지한다.

지도 관련 FSD 검사도 Pages/Widgets/Entity UI의 openURL·UIApplication·MKMapItem·CLLocationManager 직접 접근을 금지하며 대표 음성 fixture를 유지한다. 최종 전체 28 Swift 파일 구조 검사 통과.
전용 기기에서 Maps 복귀 후 기존 DB 즐겨찾기 보존, DB 공고의 NavigationLink 상세·장소 버튼과 back으로 목록 복귀도 확인했다.
