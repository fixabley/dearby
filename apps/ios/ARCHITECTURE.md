# iOS prototype structure

2026-10-04 승인: selected 전체 화면을 구현하는 네이티브 클릭형 프로토타입.
Repository/API/인증/영구 저장/OS 개인정보 접근 계층 없이 fixture + 화면 메모리로 구성합니다.

- `app/entrypoint`: ActivityViewModel과 IdentityViewModel의 앱 실행 수명, 탭 조립.
- `pages/home`: 네 탭(발견·내 활동·명함·프로필)/NavigationStack과 탐색 상세에서 탭 숨김, 설정 목적지를 조합하는 HomePage.
- `pages/intro`: 첫 실행 소개 IntroPage. 패스키 로그인은 `widgets/identity`의 SignInSheet를 띄운다. 애플 로그인(플래그 켠 빌드)은 여기서 바로 시작하고, 처음 보는 애플 계정(202)이면 SignInSheet를 열어 새 계정/패스키 계정 연결을 고르게 한다.
- `widgets/activity`: 활동 목록(서버 검색·페이지)·상세·이미지·일정표와 내 활동 상태(비로그인은 메모리, 로그인은 앱 계층이 넘긴 `MineServer`로 `/v1/me/activities`; 같은 계층의 `widgets/identity`를 참조하지 않는다).
- 추천(contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): `widgets/activity`의 `RecommendationModel`(로그인만 `/v1/me/recommendation-preferences`; 계약 "비로그인에서 만들기·추천을 막음" 2026-10-25로 비로그인 추천 쿼리·기기 저장·로그인 시 올리기를 없앴다)·`RecommendationFunnelPage`(발견 첫 진입 퍼널, 앱 루트의 fullScreenCover)(결과 버튼 `맞춤 대기열 보러 가기`는 퍼널을 닫은 뒤 대기열을 연다). 발견의 `추천` 묶음은 2026-10-25 사용자 결정으로 없애고 맨 위 `오늘의 맞춤 대기열` 한 행(`설정`으로 퍼널 다시 열기)만 남겼다(퍼널을 마치지 않았으면 퍼널 먼저). 퍼널 결과는 개수와 지원 자격 안내만 보인다. `widgets/identity`의 `EligibilityModel`·`EligibilityConsentSheet`·`EligibilityFlow`(프로필 `추천` 묶음의 `지원 자격 정보`, `/v1/me/eligibility-profile`). 두 위젯은 서로 참조하지 않고 앱 계층이 잇는다. 한 화면 한 질문 틀은 `shared/ui`의 `DearbyQuestionPage`. 퍼널을 마쳤는지는 서버 값(`completedAt`)만 본다. 비로그인이면 퍼널이 저절로 뜨지 않고, 발견의 대기열 행(보조 글자 `로그인하면 나에게 맞는 활동을 골라 드려요`, `설정` 없음)과 프로필 `관심 분야·활동 종류`는 앱 계층이 로그인 시트를 연 뒤 서버 답을 읽으면 대기열(퍼널 먼저)·퍼널로 이어 간다. 이전 빌드가 남긴 기기 키 `dearby.recommendation.preferences`·`dearby.recommendation.completed`·`dearby.discoveryQueue.local`은 읽지도 지우지도 않는다. 진입 행은 API 404에도 보이고 대기열 화면이 `아직 대기열이 없어요`를 보이며, 지원 자격 API 404는 그 행만 숨긴다.
- 맞춤 대기열(contract "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터" 2026-10-24): `widgets/activity`의 `DiscoveryQueueModel`(로그인만: 앱 계층이 넘긴 `DiscoveryQueueServer`로 `/v1/me/discovery-queue`·`/refill`·`/v1/me/recommendation-signals`)·`DiscoveryQueuePage`(발견 맨 위 `오늘의 맞춤 대기열` 행에서 앱 루트가 여는 fullScreenCover, 세로 `ScrollView` + `.paging`, 끝 페이지 `더 받기`, `⋯`은 confirmationDialog, 고른 뒤 4초 `되돌리기` 알림이 끝나야 신호를 보낸다)·`QueueStoryCard`(서버 `scenes`(HOOK·HIGHLIGHTS·ELIGIBILITY·BENEFIT·DEADLINE, 모르는 종류는 버림)를 그대로, 없으면 HOOK + 핵심 줄 2개 이상일 때 HIGHLIGHTS + DEADLINE; 4초마다 넘기고 마지막 장면이 끝나면 막대를 채운 채 멈춘다(다음 활동은 사용자가 넘길 때만), 켄 번즈 커버·글자 떠오름은 동작 줄이기에서 끔, 화면 읽기에서는 자동 넘김 없이 사용자 지정 동작). 커버가 없으면 로고 + 청록 그라데이션(API에 브랜드 색이 없다).
- 즐겨찾기·푸시(contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): `widgets/activity`의 `FavoritesModel`(로그인만, `/v1/me/favorites`; 모든 하트·종은 이 모델의 `levels`를 보고 앱 루트가 environment로 넘긴다. 발견 응답은 토큰 없이 읽어 `favorited`가 없다)·`FavoriteControls`(하트 `heart`/`heart.fill`, 즐겨찾기면 알림 수준 종 `bell.fill`/`bell`/`bell.slash`와 그 시트, 활동이면 `+` 내 활동에 추가 `plus.circle`/`checkmark.circle.fill`; 상세 우상단 `추가`는 없앴다)·`FavoritesPage`(내 활동 탭 상단 세그먼트 `내 활동 | 즐겨찾기`, 밀어서 해제 + 되돌리기, 우상단 `목록 | 캘린더`는 기기 키 `dearby.favorites.layout`)·`FavoritesCalendarView`(`UICalendarView` 감싼 월 달력 + 월별 날짜 목록)·`ProgramDetailPage`(즐겨찾기 프로그램을 누르면 여는 프로그램 상세). 맞춤 대기열 ♥도 이 즐겨찾기다(LIKE/UNLIKE 신호는 서버가 기록). `widgets/identity`의 `NotificationModel`(권한은 첫 프로그램 하트·`새 모집 알림` 켜기에서만 묻고, 허락 뒤에만 원격 알림 등록, APNs 토큰을 기기 시간대와 함께 `PUT /v1/me/push-devices`, 로그아웃·알림 끄기에서 `DELETE`)과 프로필 `알림` 묶음. `app/entrypoint/AppDelegate`가 토큰과 알림 누름(`url`, 없으면 `activityId`)을 받아 `IncomingLink`(활동, `dearby://favorites?view=calendar`)로 연다. 엔타이틀먼트 `aps-environment`는 Debug development, Release production.
- `widgets/identity`: 프로필(데이터 입력·변경 이력·단계별 이력 입력·인증자료)·명함 탭(`CardsPage`: 세그먼트로 `MyCardsPage`·`WalletPage` 전환)·설정(`SettingsPage`·`SettingsModel`: 계정·연결된 로그인·패스키·정보)·명함 제작(이름·공개 연락처·이력 선택, 내장 템플릿 `TemplateDocument` 미리보기; 앱 안 꾸미기는 2026-10-18 삭제) 화면과 모델.
- `features/application`: 로컬 신청 안내/완료 표시, 외부 HTTPS Link.
- `features/calendar`: 기기 캘린더(EventKit) 읽기 전용 겹침 비교(누를 때만 권한 요청, 기기 안 계산, 화면을 닫으면 버림), 안내/거부/결과 시트, 시간 격자. 미리보기는 `FixtureCalendarEventSource`. `캘린더에 추가`는 미리 채운 시스템 일정 추가 화면(`AddToCalendarSheet`, EventKitUI 브리지; 권한 요청·읽기 없음, 저장은 사용자가 그 화면에서).
- `entities/activity`: 활동 값 모델(일정 상태 `ScheduleStatus`·겹침 확인 시간 `OverlapCheck` 포함)·페이지와 `GET /v1/activities`(`/:id` 단건 포함) 클라이언트.
- `entities/identity`: 순수 명함/연락처/이력 fixture 및 CardView/CardDeck/HistoryTimeline/ContactIcons.
- `shared/lib`: 검색어 비교, 마크다운 부분집합 파서 `MarkdownDocument`.
- `shared/ui`: DearbyStyle 색상(시스템 중립색 별칭 + 라이트/다크 두 값의 청록 액센트 `teal`, 두 모드 같은 채움 `tealFill`과 흰 `onTeal`), DearbyLogo, 마크다운 본문 `DearbyMarkdownText`, 버튼(채움/테두리, Apple 로그인 버튼 DearbyAppleButton), 배지, 아바타,
  정보행, 세그먼트, 시트 헤더, 탭 루트 내비게이션 바 액세서리 `TabRootToolbar`([탭 주 행동] 설정 `SettingsRoute` 링크·알림 버튼·`DearbyMenuItem` 목록의 `더 보기` 메뉴, 비면 숨김), 비스크롤 화면 배치 `CenteredMessage`·`StateGlyph`·`MessageAction(s)`, 내용 로딩 스켈레톤 `dearbySkeleton()`·두 줄 행 `DearbyRowsSkeleton`(계약 "내용 로딩은 스켈레톤"). 도메인을 import하지 않으며 실제 재사용 요소만 둡니다.

프로필과 명함은 앱 수명 동안만 존재합니다. 저장/공개 선택/프리셋/보낸 표시도 메모리뿐입니다.
명함 스냅샷은 생성/편집 시 선택한 연락처와 이력을 복사하며, 기존 명함은 프로필 변경을 자동 반영하지 않습니다.
OS 공유는 활동의 Dearby 웹 주소(`<웹 origin>/activities/<id>`) ShareLink만 사용합니다. 옛 명함 공유(공유 기록·스캔)는 2026-10-18 사용자 결정으로 없고, 유니버설 링크는 2026-10-22 사용자 결정으로 `/cards/*`·`/activities/*`만 앱이 받아 `app/entrypoint/IncomingLink`로 나눕니다(계약 "유니버설 링크·App Links·활동 공유·오픈그래프").
사진은 로컬 assets만 표시합니다. QR 생성·스캔·카메라·파일 쓰기는 없습니다.

FSD 상향/동일 계층 다른 슬라이스 직접 참조 금지, 하위 2계층 제한, Pages/Widgets 공개 Shared UI
예외와 순수 UI 계약을 유지합니다. 공개 타입은 `architecture/public-api.json`, 입력+콜백만 그리는
HomePage/ActivityInformationView는 `pure-ui.json`에 기록합니다. Scene/서비스 우회 wrapper는 없습니다.

`SourceInventory`는 checkout-local Sources를 검사합니다. `PrototypeBoundaryTests`는 서비스/기기 API와
권한 문구/API origin/URL scheme/ATS가 다시 들어오는 것을 방지합니다.
단위 테스트는 선택한 정보만 명함에 포함, 메모리 수명, 저장/교환 그룹, HTTPS, 일정 계산/다음 결과를 확인합니다.
UI 테스트는 실제 기존 Simulator에서 탭/필터/저장/신청/편집/명함함/프로필/일정 비교를 확인합니다.
