# Android FSD 구조와 상태 관리

[이슈 #1](https://github.com/fixabley/dearby/issues/1)과 [공통 설계 #3](https://github.com/fixabley/dearby/pull/3)의 FSD 책임으로 전환한다. 첫 선행 변경은 여러 카드/페이지가 함께 쓰는 카탈로그·즐겨찾기·범용 UI 경계다. 이후 공고 카드, 조직 카드, 상세/라우팅을 각각 코드·테스트·문서와 함께 전환한다. 기존 게시 커밋은 재작성하지 않는다.

현재 경계:

- `entities/activitycatalog/model/ActivityCatalog.kt`: 연결된 Organization·NoticeContext·Notice·ActivityCatalog 순수 모델 단일 slice.
- `entities/activitycatalog/api/`: CatalogProvider와 AssetCatalogProvider(번들 해석).
- `features/favoriteorganization/model/FavoritesState.kt`: App이 단일 소유하는 Compose 관찰 상태.
- `features/favoriteorganization/api/`: FavoriteStore와 SharedPreferencesFavoriteStore.
- `shared/ui/NoticeFact.kt`, `shared/ui/theme/Theme.kt`: 도메인 없는 정보 표시와 테마.
- `MainActivity.kt`는 기존 manifest 컴포넌트 이름을 유지하는 App 진입점이며 `app/DearbyApp.kt`가 페이지 조합을 맡는다.
- 기존 `feature/` 화면은 다음 컴포넌트별 커밋에서 Pages/Widgets로 이동한다.

슬라이스 밖 진입점은 위에 명시한 타입/Composable이다. 모델·저장·상태 타입은 `internal`, 구현 내부 helper는 `private`로 제한한다. Kotlin 단일 모듈의 internal은 모듈 범위이지 slice 강제 경계가 아니다. 최종 구조의 경계 검사는 import/완전한 패키지 참조를 검사하는 간단한 스크립트와 리뷰로 보완한다.

기존 조직 ID, `dearby.favorites.v1` / `organizationIDs` StringSet, apply 쓰기, 동기식 번들 해석은 유지한다. state는 App에서 한 번 만들고 재생성 시 저장소를 읽는다. 화면/카드는 데이터와 콜백만 받는다. 네트워크·새 DI/상태관리 라이브러리·빌드 모듈 및 #2 UI 변경은 없다.

검증 명령: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. 기존 JVM 5건과 실제 저장/공급 계측의 참조를 새 경계에 맞춰 수정했다. 이전 3c8d2c5의 JVM5/계측7/Debug/Lint 결과는 과거 기록이며 FSD 변경 검증은 각 컴포넌트 진행 시 별도로 기록한다.

FSD 공통 경계 선행 검증(2026-09-14): JVM 5건, Debug 빌드 및 계측 APK 빌드 통과.

## 공고 카드와 탐색 페이지

`pages/discovery/ui/DiscoveryScreen.kt`는 피드·pager·피드백을 소유하고 `widgets/activitycard/ui/ActivityCard.kt`를 조합한다. 위젯의 slice 진입점은 `ActivityCard(notice, organization, contextNames, saved, position, save, showDetail)`이며 표시값과 콜백만 받는다. 상태·저장소와 다른 widget은 참조하지 않는다. 분류 표현은 entity의 `ui/ActivityClassification.kt` 진입점으로 분리했다. 상세 목적지는 App에 전달한 콜백으로 연다.

공고 카드의 새 표현은 widgets/activitycard/ui에, 피드 전용 임시 상태는 pages/discovery/ui에 둔다. ActivityCardTest는 저장소 없이 외부 저장 여부를 주입하고 버튼·더블탭·상세 콜백과 분류 표시를 검증한다. 기존 문구·padding·스타일·태그는 유지한다.

공고 카드 검증(2026-09-14): 전용 API36.1에서 ActivityCardTest 1건 통과, 제품/계측 APK 재컴파일 성공.

## 즐겨찾기 조직 카드

`pages/favorites/ui/FavoritesScreen.kt`가 ID로 표시할 조직을 고르고 각 `widgets/favoriteorganizationcard/ui/FavoriteOrganizationCard.kt`에 조직·상위 경로·연결 공고·공고별 맥락 표시 문자열·삭제/상세 콜백을 준다. 위젯은 저장소·공유 상태 및 ActivityCard widget을 참조하지 않는다. slice 진입점은 FavoriteOrganizationCard이며, 분류 표현은 동일한 Entities의 ActivityClassification을 사용한다.

조직 카드의 표시를 바꾸려면 widgets/favoriteorganizationcard/ui에, 목록 빈 상태/조직 선택 계산은 pages/favorites/ui에 둔다. 독립 위젯 테스트에서 연결 공고 분류·학교·상세/삭제 콜백과 연결 공고 없는 기존 조직의 삭제를 검증한다.

조직 카드 검증(2026-09-14): 전용 API36.1에서 FavoriteOrganizationCardTest 2건 통과, 제품/계측 APK 재컴파일 성공.
