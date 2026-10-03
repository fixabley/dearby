# iOS prototype structure

2026-10-04 승인: selected 전체 화면을 구현하는 네이티브 클릭형 프로토타입.
Repository/API/인증/영구 저장/OS 개인정보 접근 계층 없이 fixture + 화면 메모리로 구성합니다.

- `app/entrypoint`: CatalogViewModel과 IdentityViewModel의 앱 실행 수명, 탭 조립.
- `pages/home`: 탭/NavigationStack과 탐색 상세에서 탭 숨김을 조합하는 HomePage.
- `widgets/catalog`: 활동 목록·상세·예시 사진·일정표와 메모리 북마크/신청 상태.
- `widgets/identity`: 프로필·QR·받은 명함·명함 편집/선택/상세 화면과 IdentityViewModel.
- `features/application`: 로컬 신청 안내/완료 표시, 외부 HTTPS Link.
- `features/calendar`: 고정 바쁜 시간 비교, 선택 단계/결과 시트/시간 격자.
- `entities/catalog`: 활동 값 모델과 fixture 3개.
- `entities/identity`: 순수 명함/연락처/이력 fixture 및 CardView/CardDeck/HistoryTimeline/ContactIcons.
- `shared/ui`: DearbyStyle 색상, DearbyLogo, 버튼(채움/테두리), 배지, 아바타,
  정보행, 세그먼트, 시트 헤더. 도메인을 import하지 않으며 실제 재사용 요소만 둡니다.

프로필과 명함은 앱 수명 동안만 존재합니다. 저장/공개 선택/프리셋/보낸 표시도 메모리뿐입니다.
명함 스냅샷은 생성/편집 시 선택한 연락처와 이력을 복사하며, 기존 명함은 프로필 변경을 자동 반영하지 않습니다.
OS 공유는 활동의 HTTPS 예시 링크 ShareLink만 사용합니다. 명함 공유 메뉴는 외부 동작 없는 UI 예시입니다.
사진과 QR은 로컬 assets만 표시합니다. QR runtime 생성·스캔·파일 쓰기는 없습니다.

FSD 상향/동일 계층 다른 슬라이스 직접 참조 금지, 하위 2계층 제한, Pages/Widgets 공개 Shared UI
예외와 순수 UI 계약을 유지합니다. 공개 타입은 `architecture/public-api.json`, 입력+콜백만 그리는
HomePage/ActivityInformationView는 `pure-ui.json`에 기록합니다. Scene/서비스 우회 wrapper는 없습니다.

`SourceInventory`는 checkout-local Sources를 검사합니다. `PrototypeBoundaryTests`는 서비스/기기 API와
권한 문구/API origin/URL scheme/ATS가 다시 들어오는 것을 방지합니다.
단위 테스트는 선택한 정보만 명함에 포함, 메모리 수명, 저장/교환 그룹, HTTPS, 일정 계산/다음 결과를 확인합니다.
UI 테스트는 실제 기존 Simulator에서 탭/필터/저장/신청/QR/편집/명함함/프로필/일정 비교를 확인합니다.
