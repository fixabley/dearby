# 네이티브 검증 추적

2026-09-27 착수. 상태는 실행 증거가 있을 때만 변경한다. 첫 구현 이슈는 전체 제품 완료를 의미하지 않는다.

| 요구 | 담당/이슈 | 현재 증거·남은 검증 |
| --- | --- | --- |
| NAV-01 PROFILE-01 CARD-01 CARD-02 | #37 #38 #39 | 첫 구현·양 플랫폼 로컬 API 검증 통과. 전체 접근성·실기기 검증 별도 |
| AUTH-01 EXCHANGE-01 EXCHANGE-02 GUEST-01 | #37 #38 #39 | 로컬 실제 HTTP 양방향 교환·재시도·선택 가져오기 통과. 실제 메일 #42 미완료 |
| QR-01 WALLET-01 SELECT-01 | #37 #38 | QR/단독 확대·지갑 기본 UI 검증. 물리 스캔·다운로드 권한·전체 제스처 #43/#44 미완료 |
| CATALOG-01 | #45 #46 #47 | 공식 수집·카탈로그·양 앱 실제 HTTP/저장 검증 통과. 세 플랫폼 통합 검사 통과. 정기 운영 갱신 #49 미완료 |
| NOTIFY-01 | 후속 #41 | 실제 푸시와 구독 미구현 |
| CALENDAR-01 | #13 #15 참고 | 이전 이슈는 이력, 신규 OS 연동 미검증 |
| APPLY-01 | #36 | 외부 인증 이후 폼 검증 차단 |
| APPLY-02 | #46 #47 | 두 담당 checkout에서 실제 앱 내 브라우저 종료·보류·신청 기록·수정·재시작 검증 통과. 조율 통합 검사와 캡처 검토 완료 |
| DELIVERY-01 | 조율 | 웹 실행 코드 제거, 첫 앱/API 통합 및 PR #40 게시. 82ef32a 원격 API/iOS/Android CI 통과, 두 번째 구현은 진행 중 |

개인정보 검증은 선택하지 않은 연락처·이력이 공개 HTTP 응답에 없는지 확인한다. 저장 검증은 실패·취소·재시작·부분 가져오기 원본 보존을 포함한다. UI 검증은 손쉬운 사용 이름과 터치 영역, 큰 글자, 빈 상태 및 실제 화면 캡처를 포함한다. Ponytail은 복잡성만 검토하며 이 검증을 대체하지 않는다.

공통 조사 자료 이관 검증: 프로그램 28개·공고 30개·조직 26개, ID 중복 없음·조직 참조 및 이미지 파일 경로 유효. 웹 실행 제거는 2fc4875. 통합 초안 [PR #40](https://github.com/fixabley/dearby/pull/40).

## 2026-09-27 조율 checkout 실행

API 첫 구현 통합 후 Node 24.21.0: 깨끗한 `npm ci`, TCP HTTP 테스트 10/10, typecheck, lint, build 통과. SMTP는 테스트 sink이며 실제 수신 증거는 #42 미완료다. 공개 필드·철회·권한·멱등성·SQL rollback·부분 가져오기·재시작 보존을 확인했다.

CI는 [checkout](https://github.com/actions/checkout), [setup-node](https://github.com/actions/setup-node), [setup-java](https://github.com/actions/setup-java)의 현재 공식 사용법을 확인해 구성했다. macos-26 runner의 기본 Xcode는 [공식 이미지 목록](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md)에 따라 별도로 기록한다. 로컬 Xcode 27 검증과 GitHub runner 검증은 같은 결과로 취급하지 않는다. YAML 구문은 Ruby YAML로 확인했으며 원격 실행 결과는 아직 없다.

## 2026-09-27 06:24 KST 양 플랫폼 로컬 연결

iOS URLSession/AppState와 Android HttpClient/Repository가 같은 격리 API에 접속해 테스트 메일 OTP·프로필 저장·선택 공개 명함 발행·기기 명함 가져오기를 통과했다. 양방향 전달 후 양 앱의 wallet 응답에서 reciprocal=true를 확인했고 동일 요청 재전송 receipt도 일치했다. 실제 메일 발송이나 두 실기기 카메라 스캔 증거는 아니다. 플랫폼별 증거 파일은 각 담당 완료 보고 후 통합한다.

조율 코드 리뷰에서 Android 미확정 전송 A 뒤 B를 전송하면 A 재시도 키가 덮이는 경로를 발견해 수정·회귀 검사를 요청했다. 기본 반복 재시도 통과와 이 추가 경로의 완료를 구분한다. iOS QR 최종 캡처에서 승인된 배치와 차이를 발견해 카드 내부 공유 버튼·작은 메타데이터·하단 명함 목록 배치를 수정 중이다.

## 조율 checkout 최종 앱 검증

- iOS: Xcode 27, 자기 전용 Simulator 4712C750-BF32-42A8-8FBA-9AD2BA339EFC, 기본 ad-hoc signing. 최종 코드 단위/계약 17개와 게스트 5탭·로그인 gate XCUITest 1개 통과. build-for-testing, SwiftLint, 구조16도 통과했다. 로그 /tmp/dearby-root-ios-final.log 및 /tmp/dearby-root-{architecture,swiftlint}.log.
- Android: JBR 25.0.2, 자기 checkout에서 JVM18/실패0/skip0, Lint·Debug·unsigned Release 빌드 통과. 로그 /tmp/dearby-root-android-final.log. 마지막 QR 타일 텍스트 수정 후 JVM/Debug/Lint도 다시 통과했다. 담당 checkout의 에뮬레이터11 및 실제 API 결과는 플랫폼 문서에 별도 기록한다.
- A/B/A 모호한 전송의 요청 ID 덮어쓰기를 수정하고 회귀 검사를 추가했다. iOS QR 메타데이터·공유 메뉴·하단 카드 타일 최종 화면을 조율에서 직접 검토했다. 밝은 액션 색 대비/큰 글자 전체 흐름은 #14 후속이며 접근성 통과로 표시하지 않는다.
- Ponytail 검토: 불필요한 새 추상 계층 삭제 후보 없음(Lean already. Ship.). 보안·저장·UI 정확성은 위 별도 검사로 검토했다.

세 담당 Dispatch는 succeeded/retained, 통합 검증 서버와 private mail/DB는 종료·삭제 확인했다. 전체 제품 출시 완료를 뜻하지 않는다.

## 2026-09-27 활동 구현 — 조율 검사

- API: 73b2082/582a527/2f26fb6/652c0ac 소유 커밋 통합. 최종 Node24.21.0 HTTP·SQLite 테스트20, typecheck/lint/build 통과. 공식 if(kakao)/FEConf를 실제로 갱신했으며 로컬 통합 서버는 30공고/28프로그램/26조직/모집중1을 반환했다. 마지막 공식 확인 03:21:49 UTC. 운영 자동 수집/경보는 #49 미완료.
- Android: c354665/e68614c/e52e5c7/c8cf82a 통합. 조율 checkout JVM28(실패0/skip0), FSD43, Debug 빌드와 Lint(오류0/경고17) 통과. 담당의 에뮬레이터18 및 실제 API/UI/재시작3은 [플랫폼 검증](../../apps/android/docs/VERIFICATION.md)에 별도 기록. 조율 캡처 검토에서 얇은 배너·큰 글자·실제 활동 맥락 표시 확인.
- iOS: 12fd90e/d4374b1/10cd5db 통합. 조율 Xcode27 Simulator 테스트 빌드에서 30개 중28 통과, 기존 별도 인증/양방향 준비가 필요한2 skip, 실패0. 실제 catalog HTTP와 SwiftData 재개 테스트도 통과. 구조16과 strict SwiftLint54파일 위반0. 담당의 실제 앱 내 브라우저 종료/보류/신청/수정/재시작 및 profile/QR/wallet 회귀는 [화면 증거](../../apps/ios/docs/evidence/catalog/README.md)를 따른다. 조율이 최종 얇은 배너/TTL 제거 캡처를 직접 검토했다.
- Ponytail: API/Android 통합 코드에서 추가 추상화 삭제 후보 없음. Android 매초 전체 변환은 다음 경계 갱신으로 교체했다. 저장·출처·UI 정확성 검토와 복잡성 검토는 구분한다.

세 담당 succeeded/retained, 새로운 미회수 리소스0을 확인했다. 격리 API PID27463 정상 종료와 임시 DB/메일 삭제, 조율 Simulator Shutdown 상태 확인. 마지막 공식 확인 시각은 해당 로컬 실행의 증거이며 운영 최신성 보장이 아니다. 최신 원격 CI는 PR40에서 별도로 확인한다.
