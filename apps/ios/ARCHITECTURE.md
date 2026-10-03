# iOS prototype structure

2026-10-03 승인: 화면 디자인을 유지하는 클릭형 프로토타입입니다.
데이터 계층이나 mock repository가 없으며 네트워크·인증·영구저장·OS 개인정보 API를 실행하지 않습니다.

- `app/entrypoint`: 앱 실행 수명과 `CatalogViewModel` 생성, NavigationStack 조립.
- `widgets/catalog`: 탐색·상세의 기존 UI와 세션 메모리 신청 상태.
- `features/application`: 예시 신청 안내 시트와 외부 HTTPS Link.
- `features/calendar`: 고정 바쁜 시간 비교 상태와 기존 결과 시트.
- `entities/catalog`: 값 모델과 활동 fixture 3개.
- `shared/ui`: 기존 색상, 로고, 버튼 디자인.

불필요해진 pages/providers/routes 래퍼는 제거했습니다. 없는 계층을 채울 목적의 파일은 만들지 않습니다.
FSD 상향/동일 계층 슬라이스 참조 금지, 직접 하위 2계층 제한, Widgets의 공개 Shared UI 사용,
순수 표시 컴포넌트의 부작용 금지는 기존 SwiftSyntax/Harmonize 검사로 유지합니다.
`architecture/public-api.json`은 실제 남아 있는 타입만 내보냅니다.
`ActivityInformationView`는 상태나 OS API 없이 입력 값만 그리는 순수 UI 계약입니다.

`SourceInventory`는 checkout 안의 app/widgets/features/entities/shared를 확인합니다.
더 이상 존재하지 않는 pages 계층의 파일 수는 강제하지 않습니다.
`PrototypeBoundaryTests`는 production syntax에서 서비스·기기 데이터 API와 plist의
권한 문구/API origin/딥링크/ATS 예외가 다시 들어오는 것을 검사합니다.
단위 테스트는 고정 fixture, HTTPS 링크, 메모리 상태 초기화, 경계시간 및 0건 겹침을 검사합니다.
UI 테스트는 필터→상세→신청 및 일정 예시를 실제 Simulator에서 확인합니다.
