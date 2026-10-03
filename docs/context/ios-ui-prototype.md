# iOS 클릭형 프로토타입 인계

검증 시점: 2026-10-03 23:55 KST. 담당 checkout: `ios-ui-prototype`, 기준 `82ae19f`.
Orca terminal `term_235408d9-1d3e-45d3-a451-b376d51bff65`, task `task_c4e492ddf1de`, dispatch `ctx_b740729dc228`.
소유 범위: `apps/ios/**`, 이 문서. root workflow/다른 앱/운영 데이터는 수정하지 않음.

## 승인 및 구현

사용자 최신 승인에 따라 모바일 서비스 실행코드를 제거하고 기존 흰색·청록색 UI를
탐색→상세→신청/일정 예시로 단순화. 복구 태그는 `backup/mobile-service-before-prototype-20261003`.
기존 기기 데이터는 읽거나 초기화·삭제·마이그레이션하지 않음.

- `DemoActivities.swift`: 컨퍼런스 10/24 13–17시 서울 코엑스, 캠프 11/7 10–18시 서울,
  밋업 11/21 14–17시 온라인, 모두 Asia/Seoul·무료. 모집중/예정 상태는 날짜와 무관한 고정 예시.
- 2026-10-03 조율 추가 지시에 따라 404 방지를 위해 공식/신청 예시 URL을 모두 `https://example.com`으로 통일.
- 목록은 모집예정도 포함하므로 같은 스타일의 제목 `활동 둘러보기`로 조율 승인 후 변경.
- 카드·상세 여백, 타이포·컬러·버튼·일정 시트 유지. 예시 안내를 작게 표시.
- 신청 시트는 로컬 안내와 완료 표시, 외부 HTTPS 링크로 구성. 실제 접수라는 주장 없음.
  상세의 공유 버튼은 기존 디자인으로 보존하고 예시 링크만 공유.
- `CatalogViewModel`은 앱 수명 동안 신청 표시만 메모리 보유. 필터·선택은 View 메모리.
- 일정 시트는 고정 바쁜 시간 2026-10-24 14–15시와 비교. 컨퍼런스 1건, 다른 2개 0건.
- API, 인증/Keychain, SwiftData/파일 저장, EventKit, 카메라, QR/명함/프로필/숨겨진 저장 UI 제거.
  로고·GitHub mark·AppIcon 등 기존 이미지 자산은 변경하지 않음.
- API origin, ATS local networking, 권한 문구, 명함 URL scheme을 양쪽 plist/generator에서 제거.
- Debug `com.dearby.dearby`, Release `io.wid.dearby`, 버전 `0.1.0 (1)` 유지(빌드된 plist 확인).
- 빈 계층을 채우는 HomePage/DiscoveryState/route 래퍼 제거. 실제 남은 FSD 경계 검사 유지,
  없는 pages 계층의 최소 파일 수 요구만 코드/fixture와 함께 갱신.

## 검증

산출물/로그는 모두 `~/.dearby-signing/ios-ui-prototype-worker/`.

- 최종 Debug Simulator `build-for-testing`: 통과 (`build-tests-final.log`).
- 최종 Release iphoneos 무서명 build: 통과 (`build-release-final.log`).
- SwiftLint 0.65.1: 0 violations. 고정 체크섬 설치 사용, 규칙 disable 없음.
- Harmonize/SwiftSyntax 구조: 17 tests / 6 suites 통과. 새로운 프로토타입 부작용/권한 설정 방지 포함.
- 동일 production 모델 파일 4개와 `PrototypeTests.swift`를 임시 macOS Swift package에 복사하여
  XCTest 4개 통과 (`model-tests.log`): fixture/신청 메모리, 고정 바쁜 시간/0건, 경계시간, HTTPS 검증.
  이는 호스트 모델 테스트이며 iOS UI 검증을 대체하지 않음.
- 기존 iPhone Air iOS 26.5 `567FC150-8A0F-4F12-9349-F2652BF79E80` 사용을 root가 승인.
  새 Simulator/clone 없음. 첫 부팅은 Apple 로고/진행바에서 지연되어 초기 test 실행을 중단했고,
  bootstatus 완료 뒤 최종 `test-without-building` 성공: iOS XCTest 4개 + UI 3개, 실패 0.
  신청 취소/완료/상세 재진입/재실행 초기화, 필터, 숨긴 진입점 부재, 캘린더 권한 팝업 부재,
  선택 해제시 비교 비활성화, 겹침 1건/0건을 검증.
  `prototype-tests-final.xcresult`, `tests-final.log`, `ui-evidence/manifest.json`에 증거 보존.
  탐색·신청·캘린더 1건/0건 캡처를 직접 확인해 잘림 없는 표시 확인.
  상세 캡처는 push 애니메이션 도중이라 정적인 디자인 비교 증거로는 사용하지 않음.
  실기기 조작·서명·인증서/Keychain 접근 없음.
- xcodeproj generator 재실행 결과 pbxproj와 scheme 해시 동일. `git diff --check` 통과.
- 외부 브라우저 실행·OS 공유 시트, iPad/대형 글꼴/VoiceOver는 이번 UI 테스트에서 실행하지 않음.

## Ponytail 및 일반 리뷰

Ponytail 후보로 남은 단일 자식 카드 HStack과 미사용 버튼 outlined 옵션을 제거.
레이어를 채우는 래퍼와 mock repository 없이 fixture+화면 메모리로 유지.
`ActivityBrowser`는 실제 브라우저를 내장하지 않으므로 `DemoApplicationView`로 명확화.
최종 과설계 리뷰: Lean already. Ship.
정확성은 별도로 고정 날짜/시각, 0건 비교, HTTPS 검증, 재실행 초기화, 권한·서비스 API 부재를 확인.

## 남은 작업

요청된 iOS 프로토타입 구현과 검증 완료. 로컬 커밋은 worker_done에 보고.
push/PR/merge, 실기기 설치와 추가 UI 검증, 세션 retain은 조율 세션 소유.
