# iOS prototype structure

2026-10-04 승인: selected 전체 화면을 구현하는 네이티브 클릭형 프로토타입.
Repository/API/인증/영구 저장/OS 개인정보 접근 계층 없이 fixture + 화면 메모리로 구성합니다.

- `app/entrypoint`: ActivityViewModel과 IdentityViewModel의 앱 실행 수명, 탭 조립.
- `pages/home`: 탭/NavigationStack과 탐색 상세에서 탭 숨김을 조합하는 HomePage.
- `pages/intro`: 첫 실행 소개 IntroPage. 패스키 로그인은 `widgets/identity`의 SignInSheet를 띄운다. 애플 로그인(플래그 켠 빌드)은 여기서 바로 시작하고, 처음 보는 애플 계정(202)이면 SignInSheet를 열어 새 계정/패스키 계정 연결을 고르게 한다.
- `widgets/activity`: 활동 목록(서버 검색·페이지)·상세·이미지·일정표와 메모리 신청 상태.
- `widgets/identity`: 프로필(데이터 입력·변경 이력·단계별 이력 입력·인증자료)·내 명함(`MyCardsPage`)·받은 명함·명함 제작(이름·공개 연락처·이력 선택, 내장 템플릿 `TemplateDocument` 미리보기; 앱 안 꾸미기는 2026-10-18 삭제) 화면과 모델.
- `features/application`: 로컬 신청 안내/완료 표시, 외부 HTTPS Link.
- `features/calendar`: 기기 캘린더(EventKit) 읽기 전용 겹침 비교(누를 때만 권한 요청, 기기 안 계산, 화면을 닫으면 버림), 안내/거부/결과 시트, 시간 격자. 미리보기는 `FixtureCalendarEventSource`.
- `entities/activity`: 활동 값 모델·페이지와 `GET /v1/activities` 클라이언트.
- `entities/identity`: 순수 명함/연락처/이력 fixture 및 CardView/CardDeck/HistoryTimeline/ContactIcons.
- `shared/lib`: 검색어 비교, 마크다운 부분집합 파서 `MarkdownDocument`.
- `shared/ui`: DearbyStyle 색상(시스템 중립색 별칭 + 라이트/다크 두 값의 청록), DearbyLogo, 마크다운 본문 `DearbyMarkdownText`, 버튼(채움/테두리, Apple 로그인 버튼 DearbyAppleButton), 배지, 아바타,
  정보행, 세그먼트, 시트 헤더, 탭 첫 화면 제목 줄 `PageTitle`. 도메인을 import하지 않으며 실제 재사용 요소만 둡니다.

프로필과 명함은 앱 수명 동안만 존재합니다. 저장/공개 선택/프리셋/보낸 표시도 메모리뿐입니다.
명함 스냅샷은 생성/편집 시 선택한 연락처와 이력을 복사하며, 기존 명함은 프로필 변경을 자동 반영하지 않습니다.
OS 공유는 활동의 HTTPS 공식 링크 ShareLink만 사용합니다. 명함 공유(공유 기록·QR·스캔·유니버설 링크)는 2026-10-18 사용자 결정으로 없습니다.
사진은 로컬 assets만 표시합니다. QR 생성·스캔·카메라·파일 쓰기는 없습니다.

FSD 상향/동일 계층 다른 슬라이스 직접 참조 금지, 하위 2계층 제한, Pages/Widgets 공개 Shared UI
예외와 순수 UI 계약을 유지합니다. 공개 타입은 `architecture/public-api.json`, 입력+콜백만 그리는
HomePage/ActivityInformationView는 `pure-ui.json`에 기록합니다. Scene/서비스 우회 wrapper는 없습니다.

`SourceInventory`는 checkout-local Sources를 검사합니다. `PrototypeBoundaryTests`는 서비스/기기 API와
권한 문구/API origin/URL scheme/ATS가 다시 들어오는 것을 방지합니다.
단위 테스트는 선택한 정보만 명함에 포함, 메모리 수명, 저장/교환 그룹, HTTPS, 일정 계산/다음 결과를 확인합니다.
UI 테스트는 실제 기존 Simulator에서 탭/필터/저장/신청/편집/명함함/프로필/일정 비교를 확인합니다.
