# Dearby iOS 클릭형 프로토타입

현재 앱은 탐색 → 활동 상세 → 예시 신청 시트와 `겹치는 시간 확인하기`만 제공합니다.
흰색·청록색, 기존 타이포·카드·버튼·시트 배치를 유지하며 새 탭은 추가하지 않습니다.

- 예시 활동: `Sources/entities/catalog/model/DemoActivities.swift`의 3개 고정 fixture.
  날짜가 지나도 예시 모집 상태는 변하지 않습니다. 실제 모집 정보가 아닙니다.
- 화면 상태: `Sources/widgets/catalog/model/CatalogViewModel.swift`의 신청 표시와
  각 View의 필터·선택 상태만 메모리에 유지합니다. 앱 재시작 시 초기 예시 화면으로 돌아옵니다.
- 일정 비교: `Sources/features/calendar/model/CalendarConflictState.swift`의
  2026-10-24 14:00~15:00 Asia/Seoul 고정 바쁜 시간. 컨퍼런스는 1건, 캠프·밋업은 0건입니다.
- 신청은 로컬 안내/완료 표시이며 실제 접수가 아닙니다. `https://example.com`는
  예시 주소입니다. HTTPS이며 자격증명이 없는 링크만 사용하고 외부 브라우저에 위임합니다.

네트워크 API, 인증/Keychain, 영구 저장, 실제 캘린더, 카메라/QR, 명함·프로필·저장 탭,
명함 딥링크 및 서비스 전용 테스트를 제거했습니다. 로고·이미지 자산은 보존했습니다.
이전 앱의 기기 데이터는 읽기·초기화·삭제·마이그레이션하지 않습니다.
복구 기준은 저장소 태그 `backup/mobile-service-before-prototype-20261003`입니다.
서버/웹/어드민/운영 DB는 이 변경 범위 밖입니다.

## 검증

```sh
ruby apps/ios/scripts/generate_project.rb
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_architecture.sh
```

Xcode의 Dearby scheme에서 unitTests와 uiTests를 실행합니다. UI 테스트는 외부 링크를
열지 않으며 신청 표시·재실행 초기화·필터·일정 겹침/0건 흐름을 검증합니다.
기존 Simulator ID를 명시하고 `-parallel-testing-enabled NO`로 실행하면 테스트 clone을 만들지 않습니다.
Debug ID `com.dearby.dearby`, Release ID `io.wid.dearby`, 버전 `0.1.0 (1)`을 유지합니다.
실기기 설치·서명·배포는 조율 세션 소유입니다.

현재 검증 결과와 인계는 `../../docs/context/ios-ui-prototype.md`를 참고하세요.
`docs/evidence`와 `docs/context-archive`는 과거 서비스 구현의 기록이며 현재 기능 증거가 아닙니다.
