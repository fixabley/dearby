# Dearby iOS 클릭형 프로토타입

2026-10-04 최신 승인으로 selected 시안의 발견·저장·QR·받은 명함·내 프로필 5탭과
활동 상세/신청/일정 비교, 명함 편집/보기/선택/공유 메뉴를 SwiftUI로 제공합니다.
모든 사람·활동·연락처는 예시이며, 앱을 종료하면 변경 상태가 사라집니다.

- 활동 fixture: `Sources/entities/catalog/model/DemoActivities.swift`의 3개 고정 예시.
  날짜가 지나도 모집 상태가 변하지 않습니다. 실제 모집 정보가 아닙니다.
- 명함 fixture: `Sources/entities/identity/model/DemoIdentity.swift`의 가상 사람·연락처·이력.
- 상태: `CatalogViewModel`의 저장/신청 표시, `IdentityViewModel`의 예시 로그인·프로필·명함·교환·프리셋.
  View의 탭·필터·선택·검색도 모두 메모리만 사용합니다.
- 일정: `CalendarConflictState.swift`의 2026-10-24 14–15시 Asia/Seoul 고정 바쁜 시간.
  컨퍼런스 60분/1건, 캠프·밋업 0건. 결과는 30분 2열 격자로 표시합니다.
- 신청: 기존 하단 CTA→예시 안내 시트→완료 표시. HTTPS 예시 링크만 외부 브라우저로 열 수 있습니다.
- QR: 고정 `https://example.com` PNG를 표시/확대합니다. 스캔/사진 버튼은 예시 명함을 엽니다.
  카메라·사진 접근/QR 디코딩은 없습니다. 공유 메뉴도 외부 전달·클립보드·이미지 저장 없이 안내만 표시합니다.
- 예시 로그인은 서버 인증 없이 화면 상태를 전환합니다. 명함 보내기는 받은 명함의 메모리 그룹만 바꿉니다.

API·인증/Keychain·영구 저장·실제 캘린더·카메라·푸시는 없습니다.
이전 앱의 기기 데이터는 읽기·초기화·삭제·마이그레이션하지 않습니다.
복구 태그: `backup/mobile-service-before-prototype-20261003`.
서버/웹/어드민/운영 DB는 변경하지 않습니다.
사진·QR은 root가 준비한 `shared/assets/prototype` 원본의 앱 번들 사본이며 런타임 다운로드가 없습니다.

## 검증

```sh
ruby apps/ios/scripts/generate_project.rb
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_architecture.sh
```

Dearby scheme에서 unitTests/uiTests 실행. 기존 Simulator ID 명시 및 `-parallel-testing-enabled NO` 사용.
Debug ID `com.dearby.dearby`, Release ID `io.wid.dearby`, 버전 `0.1.0 (1)` 유지.
실기기 설치·서명·배포는 조율 세션 소유입니다.
검증 결과와 인계: `../../docs/context/ios-ui-prototype.md`.
기존 `docs/evidence`/`docs/context-archive`는 과거 기록으로 현재 증거가 아닙니다.
