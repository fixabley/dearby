# iOS 첫 네이티브 구현 검증 — 2026-09-27 KST

첫 명함 수직 구현의 증거이며 전체 서비스 완료 보고가 아니다. SDK는 Xcode 27.0 (27A266a), Swift 6.4 / language mode 6, iOS 27 iPhone 17 Simulator `B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE`. iOS 최소 버전은 18.0. Apple 공식 최신 스택 확인: https://developer.apple.com/xcode/system-requirements . SwiftData 명시적 save/rollback 근거: https://developer.apple.com/documentation/swiftdata/modelcontext .

## 실행 결과

| 검사 | 실제 결과 |
| --- | --- |
| 앱 단위/계약 테스트 | 17개 통과: 디스크 재열기, SwiftData 저장 실패 rollback, 계정 초안 분리, 가져오기 실패·미선택 보존, 공개 필드 요청, nullable JSON, Keychain/401 복구, HTTP 오류, 링크·연락처 URL 제한, 전송 재시도 |
| Harmonize/SwiftSyntax 구조 검사 | 16개/5 suite 통과. 실제 production graph, two-layer, public API, sibling/upward 및 pure UI 규칙 포함 |
| SwiftLint 0.65.1 | SHA256 검증 설치, 40 Swift 파일 strict 검사 위반 0 |
| Debug 및 Release Simulator 빌드 | 통과. AppIntents dependency 없음에 따른 metadata extraction skip 경고만 관찰 |
| 게스트 XCUITest | 5탭, QR 생성 로그인 필요, 프로필 로그인 게이트, 빈 지갑 통과; 6개 화면 첨부 |
| 실제 로컬 API XCTest | 실제 URLSession OTP 로그인, Keychain, 프로필 PUT, 선택 연락처만 카드 POST/공개 GET, 게스트 import, wallet GET 통과 |
| 양 플랫폼 교환 XCTest | iOS AppState.send → Android, 동일 requestId 두 번 replay 동일 receiptId/시각, Android 역방향 카드 reciprocal=true 확인. 최종 06:24:23 KST 재검증 |
| 인증 상태 XCUITest | 실제 로컬 API 계정 재실행, 프로필·편집, 명함 선택, QR 확대/복귀, 공개 연락처만 표시, 받은 명함 UI 통과. 최종 06:30:28 KST |
| 실제 QR 이미지 | 최종 Simulator 화면에서 Apple Vision으로 QR을 읽고 실제 서버 발행 cardId와 일치 확인. `qr-decode.json` |

로그의 비민감 요약은 `verification.txt`, 실제 테스트 공개 식별자는 `local-api-result.json`, `cross-platform-result.json`에 있다. 코드·토큰·개인 메일함 내용은 기록하지 않았다. 로컬 API는 coordinator가 준비한 격리 DB/테스트 전용 메일 sink이며 **실제 이메일 발송이나 운영 HTTPS 검증이 아니다**.

## 정확한 명령

저장소 루트 기준. Keychain 검사는 Simulator 기본 ad-hoc signing이 필요하다. `CODE_SIGNING_ALLOWED=NO`를 넣지 않는다. 서명 비활성화로 실행한 초기 Keychain 검사는 실패했고 정상 서명 실행으로 복구했다.

```sh
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_architecture.sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -only-testing:DearbyTests/StorageTests -only-testing:DearbyTests/ContractTests test
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -configuration Release -destination 'generic/platform=iOS Simulator' -derivedDataPath apps/ios/.build/release build
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -only-testing:DearbyUITests/NavigationTests/testGuestTabsAndLoginGate test
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -only-testing:DearbyTests/LocalAPIIntegrationTests/testActualLoginPublishAndPublicProjection test DEARBY_API_URL=http://127.0.0.1:53634
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -only-testing:DearbyTests/LocalAPIIntegrationTests/testCrossPlatformExchangeAndReplay test DEARBY_API_URL=http://127.0.0.1:53634
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -only-testing:DearbyUITests/NavigationTests/testAuthenticatedProfileAndQR test DEARBY_API_URL=http://127.0.0.1:53634
```

53634는 이 세션의 임시 테스트 서버 포트다. 일반 빌드는 API 미설정이며 API 테스트는 README의 명시적 private fixture 준비가 필요하다. 자동으로 임의 테스트 계정/로그인을 만들지 않는다. cross-platform test는 Documents/dearby-cross-platform.json의 공개 상대 profileId/cardId와 기존 실제 테스트 세션을 사용한다. 전체 test 실행에서 명시적 통합 fixture가 없는 테스트는 skip한다.

## 실제 화면

`ui-guest/manifest.json`과 `ui-authenticated/manifest.json`에 테스트·기기·타임스탬프가 있다. 모두 XCUIScreen 캡처이며 이미지 생성/모형이 아니다. guest 캡처는 초기 빈 상태 증거로, QR 선택 상태의 최신 정본은 아래 authenticated 캡처다.

- [최종 QR 카드·하단 명함 선택](ui-authenticated/833CB715-E18F-4418-BCD1-48B3C0294294.png)
- [QR 단독 확대](ui-authenticated/F0CDEE91-17CF-436E-A480-61BE13288208.png)
- [선택 공개 명함 상세](ui-authenticated/08AD43C2-368E-4E73-9A33-9915CF54AA41.png)
- [프로필](ui-authenticated/F3B4C092-96D5-4EED-8A49-5370D07C3DD3.png)
- [연락처·이력 편집](ui-authenticated/A32263B4-D641-4FE9-A569-EFAFCCE7756D.png)
- [받은 명함](ui-authenticated/84956132-F010-4DD9-8646-9ACDEA5A57F3.png)

XcodeBuildMCP 설치/실행과 AX snapshot은 성공했다. 해당 도구의 ref tap은 화면 전환이 관찰되지 않아 입력 증거로 사용하지 않았고 XCTest 실제 tap으로 대체 검증했다.

## 검토와 한계

`ponytail-review`를 적용해 diff/호출 흐름을 검토했다. AnyView를 generic Account로 대체했고 앱의 미사용 store 보유를 제거했다. 사용하지 않는 HTTP 응답 타입 인자를 없앴고 empty-body decoding을 통합했다. 프로젝트 generator는 xcodeproj의 deterministic UUID 기능을 사용해 무의미한 재생성 diff를 줄였다. 최종 복잡성 검토: Lean already. Ship. 정확성·보안·저장 회귀는 위 별도 검사로 확인했다.

- #42 실제 SMTP 수신·운영 HTTPS는 미검증. 앱스토어 서명·아카이브·배포는 이번 범위 아님.
- #43 HTTPS Universal Links/웹 fallback, 물리 카메라 두 기기 스캔, Photos 저장 허용/거부, 실제 외부 연락처 앱 호출은 미검증. Camera/VisionKit, PhotosPicker/Vision, add-only Photos 저장 코드와 권한 안내는 구현됐다.
- #41 활동 카탈로그/저장 조직·프로그램, 등록 활동 선택, APNs, 캘린더, 폼 자동입력은 연결되지 않았다. #36 if(kakao) 실제 폼 접근 차단은 유지된다.
- 큰 글자 전체 흐름/VoiceOver, 받은 카드 위아래·영역 좌우 제스처 충돌, 생성 후 선택 복귀·전송 취소/실패의 전체 UI 왕복은 추가 실기 검증 필요. Native HTTP·저장 검증을 이 UI/OS 검증의 대체로 표시하지 않는다.
