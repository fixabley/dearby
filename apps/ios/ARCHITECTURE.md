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
│   │   ├── VenueMapLauncher.swift        # URL 열기 콜백·실패 처리
│   │   ├── CalendarEditorRequest.swift   # 시스템 편집기 입력 변환·실패
│   │   ├── CalendarEventEditor.swift     # EventKitUI representable
│   │   └── CalendarEditorDelegate.swift  # 완료·취소 delegate
│   ├── Pages/
│   │   ├── Discovery/UI/DiscoveryView.swift
│   │   ├── Favorites/UI/FavoriteListView.swift
│   │   └── NoticeDetail/UI/
│   │       ├── NoticeDetailView.swift
│   │       ├── NoticeIdentityView.swift
│   │       ├── NoticeDetailField.swift
│   │       ├── NoticeIdentityFact.swift
│   │       ├── NoticeLocationView.swift
│   │       ├── VenueMapButton.swift
│   │       ├── NoticeApplicationView.swift
│   │       ├── NoticeScheduleView.swift
│   │       └── CalendarAddButton.swift
│   ├── Widgets/
│   │   ├── ActivityCard/UI/ActivityCard.swift
│   │   ├── ActivityCard/UI/NoticeFact.swift
│   │   └── FavoriteOrganizationCard/UI/FavoriteOrganizationCard.swift
│   ├── Features/
│   │   ├── AddToCalendar/Model/
│   │   │   ├── CalendarEventDraft.swift
│   │   │   ├── CalendarDatePolicy.swift
│   │   │   └── CalendarDraftMapper.swift
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
│   │       ├── Model/ActivityApplication.swift
│   │       ├── Model/ActivitySchedule.swift
│   │       ├── Model/ActivityDetail.swift
│   │       ├── Model/ActivityEvidence.swift
│   │       ├── API/
│   │       │   ├── ActivityCatalogRepository.swift
│   │       │   ├── BundleActivityCatalogRepository.swift
│   │       │   ├── OrganizationSource.swift
│   │       │   ├── SnapshotOrganizationSource.swift
│   │       │   ├── OrganizationRepository.swift
│   │       │   └── ActivityDetailRepository.swift
│   │       └── UI/NoticeClassificationView.swift
│   ├── Resources/activity-samples.json
│   └── Assets.xcassets/
├── tests/
│   ├── FavoritesStoreTests.swift
│   ├── check_fsd_boundaries.py
│   ├── VenueMapTests.swift
│   ├── CalendarDraftTests.swift
│   ├── OrganizationRepositoryTests.swift
│   └── ActivityDetailTests.swift
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
| Pages/NoticeDetail | `NoticeDetailView` | `ActivityDetail`·typed onOpenMap 및 optional 신청/활동 캘린더 콜백; 상세 표시 |
| Widgets/ActivityCard | `ActivityCard` | `ActivityNoticeSummary`·저장 여부·position·compact·onSave/onShowDetail |
| Widgets/FavoriteOrganizationCard | `FavoriteOrganizationCard<Destination>` | 조직·카탈로그·삭제 콜백·목적지 ViewBuilder; 연결 공고의 기존 NavigationLink |
| Features/AddToCalendar | `CalendarDraftMapper.application(_:catalog:)`, `activity(_:notice:catalog:mapURL:)`, `CalendarEventDraft`/`CalendarEventInterval` | App이 호출하는 순수 초안 매핑; 날짜 정책은 slice 내부 helper |
| Features/FavoriteOrganization | `FavoriteOrganizations`, `SaveOrganizationResult`, `FavoriteOrganizationsRepository`, `UserDefaultsFavoriteOrganizationsRepository` | 상태와 저장 계약; 구체 저장 구현은 App 조립 또는 독립 테스트에서 사용 |
| Entities/ActivityCatalog | `ActivityApplication`, `ActivitySchedule`, `ActivityLocation`, `ActivityVenue`, `ActivityCoordinates`, `ActivityNoticeSummary`, `ActivityCatalog` 및 Model의 `ActivityNotice`, `ActivityOrganization`, `ActivitySource`, `ActivityContext`, `ActivitySchedule`; `ActivityCatalogRepository`, `BundleActivityCatalogRepository`; `NoticeClassificationView` | 순수 모델/조회, 교체 가능한 공급, 카드·즐겨찾기의 분류 표시 |

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

## 온라인 전용 장소 지도 제외 — 리뷰 보완
이전 e0b16b2는 좌표만 검사하여 online 장소에도 유효 좌표가 있으면 버튼을 표시하는 누락이 있었다. venuesWithCoordinates는 이제 mode가 online이면 빈 배열을 반환하고 offline/mixed/unknown에서는 기존 유효 좌표 필터를 유지한다.
이번에는 online에 유효 0,0 및 -90,180 좌표를 함께 넣은 회귀와 다른 mode 보존 검사를 추가했다. standalone Swift6 지도 테스트·전체 28파일 FSD/금지 fixture 및 Simulator Debug 빌드가 통과했다(빌드 로그 build_sim_2026-09-14T10-23-44-688Z_pid15343_8e334383.log, 경고·오류 없음). 순수 필터 수정이므로 런타임은 재실행하지 않았으며 샘플·UI 배치·저장 로직은 변경하지 않았다.

## 신청 캘린더 편집기

ActivityApplication은 summary뿐 아니라 opensAt/opensOn/closesAt/closesOn/timezone/url을 보존하는 의미 있는 entity 모델이다.
App의 CalendarDraftMapper.application 호출이 Features/AddToCalendar/Model의 순수 CalendarEventDraft/CalendarEventInterval을 만든다.
CalendarDatePolicy는 엄격한 날짜·오프셋 timestamp와 시간대 검증, 충돌하는 시작 날짜 거절, 정확한 timed 구간 또는 종일 날짜 구간을 만든다.
종일 끝 날짜는 exclusive이고 자정 마감은 전날의 마감으로 해석한다. 날짜 없는 경우 버튼을 생략하며 종료 미확인은 하루짜리 종일 표시와 메모를 사용한다.
원문/정확한 마감/미확인 값은 notes에 보존하고 신청 URL과 원문 URL은 구별한다.
Pages의 NoticeApplicationView/CalendarAddButton은 별도 파일이며 App이 공급하는 optional callback만 받는다. Feature 타입을 Pages에 노출하지 않는다.
App/CalendarEditorRequest는 종일 날짜를 기기 timezone의 자정으로 변환하고 timed 구간의 원본 timezone을 유지한다.
App/CalendarEventEditor는 EKEventEditViewController를 조립하고 CalendarEditorDelegate는 완료/취소시 닫는다. 실제 저장은 사용자가 OS 편집기에서 선택한다.
[Apple EventKitUI 안내](https://developer.apple.com/videos/play/wwdc2023/10052/)에 따라 권한 요청·캘린더 읽기·직접 저장·초대자·알람을 추가하지 않는다.
캘린더 선택 불가 상태는 OS 편집기가 처리하며 사용자는 취소할 수 있다. 앱은 편집기 표시를 저장 성공으로 보고하지 않는다.

이번 신청 테스트는 KRC/DB 정확 KST, 대회 자정 마감, 마감만/시작만/미상/잘못된 값/역전, 날짜 범위/혼합 정밀도, URL·원문 분리와 기기 시간대별 종일 날짜 보존을 검증한다.
실행 명령은 README의 calendar 테스트 명령이며 신청 구현 Simulator Debug 빌드와 구조 검사를 실행했다. 활동 phase와 실제 편집기 UI 검증은 후속 변경에서 수행한다.

## 활동 단계별 캘린더 — 최종 확인

ActivitySchedule은 endsOn(포함 날짜)/timezone/onlineUrl/mode와 기존 시각을 보존하는 자체 entity 파일이다.
CalendarDraftMapper.activity는 EXACT phase == venue.phase로 연결한다. 온라인 단계는 무조건 location 온라인과 검증된 onlineUrl 또는 접속 URL 미확인을 사용해 결선 장소를 누출하지 않는다.
오프라인 단계의 일치하는 장소는 모두 name/address로 합치고, 각 좌표 지도 링크는 App이 주입한 VenueMapLink로 notes에 포함한다. 미일치 장소는 장소 미확인이다.
복수 장소를 임의의 단일 pin으로 줄이지 않으며 structuredLocation은 추가하지 않는다.
Pages/NoticeScheduleView는 각 schedule 위치의 optional callback으로 같은 CalendarAddButton을 표시한다. App의 단일 활성 CalendarEditorRequest가 편집기 수명을 소유한다.
CalendarEditorRequest.prepare는 검증된 요청을 주입 presentation 콜백에만 전달하고 잘못된 요청에는 실패 콜백을 부른다. OS 오류/캘린더 선택은 EventKitUI에서 처리하며 앱은 캘린더를 읽어 존재 여부를 사전 검사하지 않는다.

엄격 날짜 정책은 잘못된 날짜/timestamp/offset/시작 날짜 모순/역전을 거절하고, 실제 시간값이 있으면 끝 timestamp를 우선한다.
정확한 시작·끝은 timed, 나머지는 소스 timezone의 날짜를 사용한다. 날짜-only 종료는 다음 날짜 exclusive, 자정 끝은 해당 날짜 exclusive다.
종일 이벤트는 DateComponents로 저장하고 EventKit 입력 시 device timezone 자정으로 변환하여 LA 등 기기 timezone에서도 한국 기준 날짜를 유지한다.
notes에 원래 summary·정확한 시작/끝·시간대·미확인 항목을 남기고 한 시간짜리 종료를 만들어내지 않는다.

이번 canonical 리소스는 공통 PR #6의 SHA256 c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f와 byte 일치한다.
이 checkout의 shared snapshot은 변경하지 않아 #6 병합 전 samples:check 차이는 예상된다. 기존/최종 JSON 각각 calendar·상태 테스트를 실행하고 map 테스트도 유지했다.
순수 검사는 KRC/DB timed KST, 대회 신청9/11→exclusive10/8, 예선10/14→exclusive10/15 온라인, 결선11/4와 일치 장소,
복수 장소/미일치 단계/온라인 URL 유무·오류, 신청 URL과 원문 분리, 종료 미확인, 잘못된/역전/모순 값, LA/서울/Auckland 종일 변환, 주입 adapter의 오류/성공을 통과했다.
전체39파일 FSD 및 Pages EventKit 접근·권한요청/직접저장/자동알람 금지 fixture 통과, 최종 Simulator Debug 빌드 성공(경고/오류 없음, build_sim_2026-09-14T10-41-36-598Z_pid15343_20ec83d8.log).

전용 Dearby-Issue1-iOS iOS26.5에서 KRC 신청 버튼→실제 EventKitUI 편집기(8/27 09:00~9/15 13:00, 신청 URL)와
활동 버튼→행사9/15 14:00~16:00 및 도서관 장소를 확인하고 둘 다 Escape 취소로 상세 복귀했다. 추가/저장 버튼은 누르지 않았고 캘린더 권한 요청도 나타나지 않았다.
원격 편집기 접근성 트리는 도구에 노출되지 않아 screenshot으로 값을 확인했고 창 focus 실패로 좌표 취소 대신 Escape를 사용했다.
증거는 build/calendar-regression/application-editor.png, application-cancelled.png, activity-editor.png 및 UI JSON이다.
종일/온라인 실제 OS 화면, 캘린더 없는 계정/원격 editor 실패/실기기는 미검증이다. 전자는 순수 변환 테스트로 검증하고 후자는 OS 처리와 취소 경로에 맡긴다.
기존 실제 터치 스와이프·물리 햅틱·전체 접근성 한계는 유지한다. 마지막 순수 adapter 보강 후에는 빌드·독립 검사를 재실행했고 runtime 기록은 그 직전 동일 편집기 UI 경로다.

## 조직 원본·cache-aside 조회
Entities/ActivityCatalog/API의 OrganizationSource.fetch(id:)와 SnapshotOrganizationSource는 조직 레코드를 별도 저장한다. OrganizationRepository는 처음 비어 있는 독립 ID 캐시를 조회하고 miss에서만 source를 호출하며 성공한 레코드만 캐시한다. path(to:)는 parent ID를 cycle-safe하게 따라가는 일시 projection이며 누락된 상위에서도 복구 가능한 경로를 유지한다. MainActor에서 replaceSource가 모든 캐시를 지워 이름/부모 변경을 반영한다.
이번 독립 source 호출 횟수 검사는 cold/hit·공유 상위·nil/없는ID·cycle/고아·전체 snapshot rename/reparent 무효화를 확인한다. 상세 projection 통합은 같은 작업의 다음 기능 커밋에 적용한다.


## ActivityDetail 조회 경계 (2026-09-14)

`ActivityDetailRepository.detail(id:)`가 상세의 유일한 투영 진입점이다. App의 `ContentView`는 로드 시 한 번 저장소를 소유하고 상세를 열 때 같은 인스턴스를 사용한다. Pages/NoticeDetail는 상세 값·콜백만 받으며 카탈로그·원본 공고·저장소를 읽지 않는다. 카드와 즐겨찾기는 같은 snapshot의 기존 카탈로그 계약을 유지한다.

조직 원본 `OrganizationSource`와 ID 캐시는 별개다. `OrganizationRepository`는 성공한 조회만 캐시하고 parent ID를 cycle-safe하게 따라가며, 알 수 없는 부모에서는 복구 가능한 경로를 반환한다. 선택 ID는 전역 leaf 조건 없이 그대로 보존한다. `replaceSnapshot`은 source와 catalog를 함께 교체하고 모든 레코드 캐시를 비운다. 별도의 경로 캐시나 영구 캐시가 없으며 모두 MainActor에 한정된다. 현재 실시간 갱신 UI는 없고, 향후 갱신 기능은 App 상태 갱신과 이 교체 메서드를 함께 연결해야 한다.

`ActivityDetail`는 Codable/저장 모델이 아닌 일시적 읽기 결과다. 조직은 원본에서 ID로만 참조하고 조직 레코드는 별도 snapshot source에 둔다. 결과의 organizationPath는 선택 노드의 관련 경로만 포함하며 전체 조직 트리를 복제하거나 저장하지 않는다. organizationLinks와 contexts는 명시된 role/ID를 유지하고 경로에서 주최 역할을 추론하지 않는다.

`aiDescription`은 기존 검토 샘플 summary이며 `descriptionProvenance = reviewed_sample.summary`로 출처를 구분한다. 새로운 AI 생성으로 표시하지 않는다. applicationInformation은 기존 날짜·URL·summary와 channels/requiredDocuments/submissionLocations를 보존한다. schedule의 period와 정확히 phase가 일치하는 장소를 getter에서 한 번 묶고 온라인 단계에는 오프라인 장소를 넣지 않는다. CalendarDraftMapper는 이 상세 값만 사용하며 기존 엄격한 날짜 정책은 유지한다. 지도는 상세의 기존 location 값을 사용한다.

`ActivityEvidenceDecoder`는 구조화 JSON을 Decoder로 순회하여 evidence/coordinateEvidence의 sourceId·locator·fieldPath를 보존한다. quality issue의 명시 fieldPath를 우선하고 배열 위치는 경로에 남긴다. 상세는 관련 source 레코드(kind/checkedAt/access/note 포함)와 근거 URL을 제공하며, 알 수 없는 source ID도 근거에서 삭제하지 않는다. 원본 JSON의 추가 메타데이터 전체를 앱에 영구 복제하는 것은 아니며 변경 없이 번들에 유지한다.

상세 UI의 ActivityCatalog/ActivityNotice 참조를 lexical checker의 부정 fixture로 금지한다. 단일 Swift 모듈이므로 컴파일러가 slice를 격리하는 것은 아니다. 새 상세 항목은 entity의 의미 있는 필드와 getter를 갱신하고 페이지의 동일 slice UI 파일에 표시하며 OS 동작은 App에서 주입한다.


### 이번 상세 투영 검증 결과

2026-09-14 Swift 6 standalone: OrganizationRepositoryTests(콜드/히트 fetch 수·공유 부모·missing·cycle·snapshot rename/reparent), ActivityDetailTests(동일 저장소 두 번 열기·선택 부모 ID·맥락 역할·전체 원본 evidence 수/경로/unknown source·출처 메타데이터), CalendarDraftTests(기존 엄격 날짜/phase/복수 장소/URL/adapter), FavoritesStoreTests(관찰·저장/복원), VenueMapTests 모두 통과했다. 상세와 캘린더 검사는 old shared JSON 및 최신 앱 JSON 각각 실행했다. README 명령으로 재현할 수 있다.

`python3 apps/ios/tests/check_fsd_boundaries.py`: 45 Swift 파일 및 부정 fixture 통과. `git diff --check` 통과. XcodeBuildMCP `build_sim`(CODE_SIGNING_ALLOWED=NO, 전용 simulator 대상) 성공, 경고/오류 없음; 로그 `build_sim_2026-09-14T11-20-38-165Z_pid15343_9fc86060.log`.

이번에는 UI 필드 공급만 바꾸고 기존 OS 어댑터·문구·레이아웃을 유지했으므로 native editor/Maps 런타임을 재실행하지 않았다. 앞선 캘린더 실제 열기·취소와 지도 handoff는 이전 검증이며 이번 결과로 간주하지 않는다. source는 in-memory snapshot이고 실시간 refresh UI·영구 캐시·네트워크는 구현하지 않았다. 번들 canonical SHA c649b0a1 및 사용자 즐겨찾기/기기 설정은 변경하지 않았다.
