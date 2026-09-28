# iOS 활동 검증 — issue #46

2026-09-27 KST, Xcode 27 / Swift 6, iOS 27 iPhone 17 Simulator `B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE`. 기본 Simulator ad-hoc signing. 운영 서비스·실기기 완료 증거는 아니다.

## 실제 결과

| 검사 | 결과 |
| --- | --- |
| Catalog/ExchangeActivity/Storage/Contract XCTest | 27개 통과. 시간 경계/24시간/만료/미확인, DTO/URL, save rollback, failed refresh 보존, successful empty 교체, cache 손상 네트워크 복구, 실제 디스크 reopen, context 상호배타·HTTPS query 보존 |
| Harmonize/SwiftSyntax | 16개 통과, FSD/두 레이어/공개 API/State·pure UI 기존 규칙 유지 |
| SwiftLint 0.65.1 strict | 위반 0 |
| Debug / Release Simulator build | 통과. AppIntents 미사용 metadata extraction skip 경고만 관찰 |
| 실제 catalog HTTP + SwiftData reopen | 통과. 실제 격리 API의 30개 수집 활동/현재 모집1개, 프로그램·조직·신청 기록 디스크 복원 |
| 실제 catalog XCUITest | 12:28:53 KST, 54.784초, 실패0, xcodebuild exit0. source-only 닫기 질문 없음, 신청 닫기 질문, 나중에 미변경, 신청 자기기록/고정 배너, 조직·프로그램 저장, 앱 종료/재실행 복원, 신청하지 않음으로 수정 |
| 기존 profile/QR/wallet XCUITest | 12:30:56 KST, 28.174초, 실패0. 프로필/편집, QR 선택·확대·복귀, 공개 연락처만 표시, 받은 명함 확인 |
| 기존 실제 auth/profile/card HTTP | 12:29:12 KST 재검증 통과. private test-mail fixture OTP, Keychain, 프로필 PUT, 공개 projection, guest import/wallet. 실제 이메일 수신 증거 아님 |

27개 단위 테스트 실행 이후 기능 로직 변경은 초기 refresh의 root 수명 이동과 UI 문구/표시 정리뿐이다. 전체 UI 통과 후 중복 진단 캡처 두 개와 테스트 runner의 임시 PNG 직접 기록을 제거했다. 이후 coordinator 검토로 내부 TTL인 “정보 유효 기한” 표시 한 줄만 제거했으며 source-only UI 캡처를 추가했다. 아래 manifest에는 전체 흐름이 통과한 원본 11장(중복 진단 포함)을 보존하고, 최신 출처 화면은 별도 source-final 증거가 우선한다.

최종 출처 화면 재검증: `testSourcePresentation` 통과, [TTL 표시를 제거한 최신 출처/신청 안내](source-final/792EA408-CA9F-4CDC-AD14-D395F73756F9.png). 내부 만료 판정은 유지하고 사용자에게는 공식 확인 시각과 실제 모집 마감만 제공한다.

## 실제 화면

모두 통과한 XCUITest의 XCUIScreen 원본 PNG. `manifest.json`에 기기·테스트·시각이 있다. 외부 공식 사이트는 열고 닫기만 했으며 외부 로그인/신청 제출은 실행하지 않았다.

- [모집 중 발견](BBADCB11-72C6-42CC-912C-1FEBC0E50AAE.png)
- [일정과 미정 시각](AB114ED6-C05F-4CC2-8EC6-6A5D849F209F.png)
- [프로그램·조직 기기 저장](CEEADA82-14F5-4928-870C-3F6803E3EB82.png)
- [앱 내 공식 브라우저](A63FF787-E88B-4140-BF95-F7F37C2D4DA0.png)
- [신청 여부 직접 기록](ACFB4583-208D-48D8-BD3A-B197730EA5B1.png)
- [얇은 고정 신청 배너와 주최 확인 구분](D462D56B-40B5-4D3D-A8AF-9A213B1303F4.png)
- [재시작 후 저장 복원](034A1E90-AF5F-49B1-8753-4005E8EDA9D6.png)
- [재시작 후 신청 기록 복원](5D2BC2BB-D4B9-4C77-8B6E-5C7E9B01B42A.png)
- [신청 기록 수정](AE362FC5-9B2E-4ADA-9AC9-272FB6A76216.png)

발견/상세/배너 이미지를 직접 열어 white/legible teal, 원문 일정 유지·시간 미정, 헤더 아래 체크 한 줄 고정, 신청 확정 구분을 검토했다.

## 재실행 명령

자기 checkout 루트에서 실행. 52777은 coordinator 소유의 실제 격리 API 임시 포트이며 기본 앱 설정에 포함하지 않는다. DB는 실제 공식 페이지 수집 결과다. 테스트 target fixture를 production catalog로 사용하지 않는다.

```sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_architecture.sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -parallel-testing-enabled NO -only-testing:DearbyTests/CatalogTests -only-testing:DearbyTests/ExchangeActivityTests -only-testing:DearbyTests/StorageTests -only-testing:DearbyTests/ContractTests test
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -parallel-testing-enabled NO -only-testing:DearbyTests/CatalogAPIIntegrationTests test DEARBY_API_URL=http://127.0.0.1:52777
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -destination 'platform=iOS Simulator,id=B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE' -derivedDataPath apps/ios/.build -parallel-testing-enabled NO -only-testing:DearbyUITests/CatalogNavigationTests test DEARBY_API_URL=http://127.0.0.1:52777
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby -configuration Release -destination 'generic/platform=iOS Simulator' -derivedDataPath apps/ios/.build/release build
```

## 실패를 통해 수정한 점과 한계

- iOS 한국어 SFSafari 닫기 버튼은 “닫기”였다. 테스트 선택자 수정.
- iOS 27 confirmationDialog는 cancel-role 버튼을 별도 “나중에”로 표시하지 않았다. 명시적 나중에 버튼으로 수정, 실제 tap과 미변경 검증.
- 팝오버가 표시 중일 때 즉시 tap하면 이벤트가 적용되지 않았다. 두 번째 질문도 존재를 기다린 뒤 tap하도록 수정. 저장 실패로 오판하지 않고 실제 화면과 hierarchy로 확인했다.
- SwiftUI List의 화면 밖 row는 존재/탭 가능을 보장하지 않는다. 조직 저장·저장 해제를 실제 scroll 후 검증했다.
- 혼합 unit/UI 대상 실행의 cleanup이 지연되었다. UI-only 및 `-parallel-testing-enabled NO`로 분리 실행해 정상 종료를 확인했다. 중단/실패 결과는 통과 evidence에 포함하지 않았다.
- XcodeBuildMCP snapshot은 읽혔으나 tap 후 전환은 확인되지 않아 입력 증거로 쓰지 않았다. 실제 XCTest tap/스크린샷이 정본이다.
- 등록 활동 picker는 모델·wire·링크·과거/미확인 표시 회귀로 검증했다. 실제 두 계정 전달/등록활동 picker 전체 UI 왕복, VoiceOver·극대 글자, OS 캘린더/푸시, Safari 외부 인증 이후 복귀·실제 폼은 이번 실행에서 검증하지 않았다.

Ponytail diff/호출 흐름 검토: 필요한 SwiftData/State/FSD 경계 유지, speculative repository/새 dependency 없음. 임시 진단 PNG 기록과 중복 캡처는 제거. 최종 추가 삭제 후보: Lean already. Ship. 정확성·저장·UI 검사는 별도 위 결과로 확인했다.
