# FSD 전환 진행 — 공고 카드 컴포넌트

2026-09-14, [#1](https://github.com/fixabley/dearby/issues/1) / [설계 #3](https://github.com/fixabley/dearby/pull/3).
공고 카드 진입점은 `Widgets/ActivityCard/UI/ActivityCard.swift`의 internal `ActivityCard`다. 표시 모델과 저장 여부·콜백만 받으며 내부 요약 `NoticeFact`는 같은 파일의 private 타입이다.
카드가 의존하는 공고·조직·출처·카탈로그는 하나의 응집된 `Entities/ActivityCatalog` slice로 함께 옮겼다. `Model/ActivityCatalog.swift`의 모델·조회, `API/ActivityCatalogProviding.swift`와 `BundleActivityCatalogProvider.swift`의 공급 경계, `UI/NoticeClassificationView.swift`가 진입점이다.
Entities는 Widgets를 모르며 같은 레이어의 다른 slice를 참조하지 않는다. 실제 FSD 구조 검사는 `python3 apps/ios/tests/check_fsd_boundaries.py --slice Widgets/ActivityCard --slice Entities/ActivityCatalog`로 실행한다.
이번 카드 컴포넌트 검사: 경계 검사(상향·다른 slice·UI 상태/저장 접근 금지 음성 사례 포함), 독립 Swift 상태/공급/복원 검사 및 Simulator Debug 빌드 통과.
다음 컴포넌트 커밋에서 즐겨찾기 카드/행동과 페이지 라우팅을 옮긴다. 아래는 이전 aa4a258 구조·검증 기록이며 이번 FSD 최종 상태가 아니다.

---

# iOS 앱 구조

[이슈 #1](https://github.com/fixabley/dearby/issues/1)의 승인된 Seed v2와 [공통 설계 PR #3](https://github.com/fixabley/dearby/pull/3)을 적용한다.
긴 화면 파일에서 탐색·즐겨찾기·상세와 저장을 함께 다루던 책임을 나눴다. 기존 화면·문구·탭·제스처·샘플·저장 형식을 유지한다.
외형 정비는 [#2](https://github.com/fixabley/dearby/issues/2)의 범위다. 새 외부 상태 관리/DI 라이브러리, 빌드 모듈, 네트워크 및 API 변경은 없다.

## 실제 디렉터리 트리

```text
apps/ios/
├── Dearby.xcodeproj/                 # 기존 synchronized Dearby 그룹·공유 scheme 유지
├── Dearby/
│   ├── App/
│   │   ├── DearbyApp.swift           # 실제 의존성 생성, 즐겨찾기 단일 소유
│   │   └── ContentView.swift         # 로딩/오류·탭·NavigationStack 조합
│   ├── Features/
│   │   ├── Discovery/
│   │   │   ├── DiscoveryView.swift   # 세로 피드, 상세 sheet, 저장 피드백
│   │   │   ├── ActivityCard.swift    # 표시 데이터·저장/상세 콜백
│   │   │   └── NoticeFact.swift      # 카드 전용 요약
│   │   ├── Favorites/
│   │   │   ├── FavoriteListView.swift
│   │   │   └── FavoriteOrganizationRow.swift
│   │   └── NoticeDetail/
│   │       ├── NoticeDetailView.swift
│   │       └── NoticeIdentityView.swift
│   ├── Shared/
│   │   ├── Models/ActivityCatalog.swift
│   │   ├── State/FavoriteOrganizations.swift
│   │   ├── Data/
│   │   │   ├── ActivityCatalogProviding.swift
│   │   │   ├── BundleActivityCatalogProvider.swift
│   │   │   ├── FavoriteOrganizationsStorage.swift
│   │   │   └── UserDefaultsFavoriteOrganizationsStorage.swift
│   │   └── UI/NoticeClassificationView.swift
│   ├── Resources/activity-samples.json
│   └── Assets.xcassets/
├── tests/FavoritesStoreTests.swift   # 앱·SwiftUI 없이 실행
├── ARCHITECTURE.md
└── README.md
```

`Dearby/` 아래 Swift 파일과 리소스는 기존 Xcode 파일 시스템 동기화 그룹이 자동 포함한다.
테스트 실행 파일과 증거는 무시되는 `build/`에 생성하며 앱 타깃에 넣지 않는다.

## 책임과 의존 방향

```text
DearbyApp → ContentView → Features → Shared/Models, Shared/UI
    │           │
    │           └→ ActivityCatalogProviding → BundleActivityCatalogProvider
    └→ FavoriteOrganizations → FavoriteOrganizationsStorage → UserDefaults 구현

사용자 이벤트 → 화면 콜백 → 단일 FavoriteOrganizations 변경
             → ContentView의 Observation 읽기 → 두 탭에 새 ID 집합 전달
```

- App만 실제 공급·저장 구현을 조립한다. ContentView는 카탈로그 공급 protocol을 호출하고 성공/실패를 기존 화면으로 표시한다.
- Features와 Shared/UI는 표시 모델·ID 집합·콜백만 받는다. UserDefaults, Bundle, 파일이나 저장소를 직접 접근하거나 공유 상태를 생성하지 않는다.
- 모델은 SwiftUI·Observation·저장소를 모르며, 조직 경로/분류/피드 등의 계산은 순수하다. JSON 파일 읽기와 버전·모드 확인은 Bundle 공급자에 둔다.
- `FavoriteOrganizations`는 구체 저장소를 모르고 protocol의 `load/save`만 사용한다. 사용자 이벤트 및 저장은 MainActor에서 직렬 실행한다.
- 여러 화면에서 실제 재사용하는 분류 표시만 Shared/UI에 둔다. 카드 요약과 상세의 정보 표시처럼 요구하는 모양이 다른 컴포넌트는 기능 안에 둔다.
- 기본 TabView, NavigationStack, List, ScrollView, Button, sheet를 우선한다. 구조 정리만으로 외형이나 네이티브 UI 스타일을 바꾸지 않는다.

## 상태 소유와 생명주기

| 상태 | 소유자·수명 | 읽기·변경 |
| --- | --- | --- |
| 즐겨찾기 ID 집합 | DearbyApp의 단일 `@State`가 `@Observable FavoriteOrganizations`를 앱 수명 동안 유지 | ContentView가 읽고 탭에 값·이벤트 콜백 전달; 저장·삭제는 같은 인스턴스로 모임 |
| 카탈로그·로딩 오류 | 루트 ContentView의 `@State`; 기존 `.task` 로딩과 다시 시도 유지 | 주입된 `ActivityCatalogProviding.load()` 결과로 갱신 |
| 발견 상세 선택·저장 피드백·햅틱 카운트 | DiscoveryView의 로컬 `@State` | 해당 화면의 이벤트가 변경; 전역 상태로 승격하지 않음 |
| 탭 선택·각 탭의 내비게이션·스크롤 위치 | 기존 SwiftUI TabView/NavigationStack/ScrollView 수명 | 기존 시스템 동작 유지; 새로운 라우터나 별도 캐시를 만들지 않음 |

ContentView는 **지연 생성되는 탭/내비게이션 클로저 앞에서** `favorites.ids`를 읽어 Observation 의존성을 등록한다.
이 읽기를 내부 클로저로 옮기면 상태 테스트는 통과해도 실제 탭의 값이 오래된 상태로 남을 수 있으므로 화면 회귀를 함께 확인한다.
여러 소비자는 별도 상태 사본을 소유하지 않고 루트가 제공한 최신 값으로 렌더링한다. 새로운 창도 앱의 같은 상태를 사용한다.
외부 프로세스의 UserDefaults 변경을 실시간 감시하는 기능은 없으며 저장소 복원은 상태 생성 시 수행한다.
Preview는 빈 값을 읽고 쓰기를 버리는 전용 저장소로 앱 사용자의 즐겨찾기에 접근하지 않는다.

## 저장·모델 호환

`dearby.favoriteOrganizationIDs.v1` 키에 정렬된 문자열 배열을 저장하고 읽을 때 Set으로 중복을 제거한다.
추가는 토글이 아닌 멱등 저장이고 해제는 명시적 삭제다. 기존에 저장된 운영부서나 현재 모델에 없는 ID도 임의 변환·삭제하지 않는다.
기존 ID를 표시할 수 있는 조직만 목록에 렌더링하는 규칙도 유지한다.

한국농어촌공사와 DB손해보험은 각각 기업 ID를 저장한다. 학교 맥락·분류·부모 관계는 별개이며 상위 조직/학교를 자동 저장하지 않는다.
영남권 대회는 교육원 아래 지속 프로그램 ID를 저장하고 회차는 개별 공고에 남긴다.
[활동 규격](../../docs/product/activity-data-v1.md)과 [관심 대상 규칙](../../docs/product/interest-target-rules.md)을 따른다.

## 새 기능 배치 예시

- 카드에 기존 모델의 요약 표시를 추가한다면 `Features/Discovery/ActivityCard.swift` 또는 같은 폴더의 전용 View에 둔다.
- 즐겨찾기 행 표현은 `Features/Favorites/`에 둔다. 발견과 즐겨찾기가 같은 표시를 실제로 공유할 때 `Shared/UI/`로 옮긴다.
- 상세만 필요한 펼침/접힘은 `NoticeDetail`의 로컬 `@State`에 둔다. 기능 간 공유가 필요한 새 상태라면 App에서 하나 소유하고 표시 값/콜백을 주입한다.
- 저장 구현 교체는 `Shared/Data`의 protocol 구현과 App 조립을 바꾼다. UI나 상태 객체에 구체 저장소 호출을 추가하지 않는다.
- 후속 서버 카탈로그 공급은 `ActivityCatalogProviding` 경계에서 시작한다. 현재는 동기 번들 로딩에 맞춘 최소 protocol이며, 네트워크 도입 시 async·취소·오류 정책을 해당 작업에서 정한다. 인증·재시도·페이지네이션은 미리 구현하지 않는다.
- 새 모델 조회는 `Shared/Models`에 둔다. 공통 계약 변경이 필요하면 coordinator와 먼저 조율하며 iOS에서 공통 샘플이나 Android 리소스를 임의 수정하지 않는다.

## 검증 기록

2026-09-14 이번 리팩터링에서 실행했다. 명령은 [README](README.md)를 따른다.

| 검사 | 결과·실행 범위 |
| --- | --- |
| 독립 `swiftc -swift-version 6` | 인메모리 저장소 주입·추가/중복/삭제·두 Observation 소비자의 알림/동일 값, 실제 UserDefaults 배열 호환과 새 저장소·상태 복원 통과 |
| 모델·공급자 | 실제 공통 JSON 디코딩, 기업/학교 분리·대회 경로/회차, 번들 공급 교체·잘못된 모드/손상 JSON 오류 통과 |
| Xcode Simulator Debug | Xcode 26.6, iOS 26.5 전용 iPhone 17 Pro, `CODE_SIGNING_ALLOWED=NO` 빌드·실행 성공 |
| 공통 데이터 | 루트 `npm test` 13건 통과, `python3 scripts/sync-activity-samples.py --check` 일치 |
| 실제 화면·저장 | 전용 기기에서 하단 탭·빈 목록·저장 버튼·목록·연결 공고 상세의 기업/학교 분리·명시적 삭제와 카드 반영 확인 |
| 더블탭·중복 | Orca computer의 전용 Simulator 창 좌표 `--click-count 2`로 미저장 DB 카드를 저장하고 반복 후 두 기업이 각 한 행으로 유지됨을 화면 확인 |
| 재실행 | 앱 종료/재실행 후 두 기업 유지, KRC 삭제 후 다시 종료/재실행하여 DB만 남음 확인 |
| 카드 이동 | 접근성 `scroll down`으로 1/4 KRC → 2/4 DB 전환 확인; 합성 터치 드래그는 이동을 확인하지 못했으므로 터치 스와이프 회귀 통과로 기록하지 않음 |

전용 기기 `Dearby-Issue1-iOS`를 새로 사용했다. 기존 사용자 시뮬레이터의 즐겨찾기는 초기화하거나 삭제하지 않았다.
로컬 증거는 `build/regression/`의 `favorites-two-organizations.png`, `restored-after-relaunch.png`, `deletion-restored.png`에 남긴다(커밋 제외).
MCP 접근성 snapshot/elementRef 입력은 화면과 다른 상태를 보고한 경우가 있어 성공 응답만으로 동작을 판정하지 않고 Orca 창 조작과 스크린샷으로 보완했다.

실제 터치 스와이프, VoiceOver 전체 흐름, 최대 글자 크기, iPad·가로 화면, 실기기 전체 검증은 미완료다.
독립 상태 검증은 SwiftUI 화면 테스트가 아니며 이번 수동/도구 회귀를 상시 XCUITest로 자동화한 것은 아니다.
API 연결·로그인·개인화·원문 자동 추출·Q&A/커피챗/크레딧·트리 탐색은 기존과 같이 범위 밖이다.
