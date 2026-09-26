# 네이티브 검증 추적

2026-09-27 착수. 상태는 실행 증거가 있을 때만 변경한다. 첫 구현 이슈는 전체 제품 완료를 의미하지 않는다.

| 요구 | 담당/이슈 | 현재 증거·남은 검증 |
| --- | --- | --- |
| NAV-01 PROFILE-01 CARD-01 CARD-02 | #37 #38 #39 | 구현 중, 빌드/상대 기기 검증 대기 |
| AUTH-01 EXCHANGE-01 EXCHANGE-02 GUEST-01 | #37 #38 #39 | 구현 중, 실제 메일·양 플랫폼 HTTP 통합 대기 |
| QR-01 WALLET-01 SELECT-01 | #37 #38 | 구현 중, 제스처·다운로드·스캔 검증 대기 |
| CATALOG-01 | 후속 #41 | 과거 9/24 스냅샷만 보존, 수집/갱신 미구현 |
| NOTIFY-01 | 후속 #41 | 실제 푸시와 구독 미구현 |
| CALENDAR-01 | #13 #15 참고 | 이전 이슈는 이력, 신규 OS 연동 미검증 |
| APPLY-01 | #36 | 외부 인증 이후 폼 검증 차단 |
| APPLY-02 | 후속 #41 | 신청 여부 수동 확인 흐름 미검증 |
| DELIVERY-01 | 조율 | 웹 실행 코드 제거, 3개 하위 세션 입력 수락; 통합/PR 진행 중 |

개인정보 검증은 선택하지 않은 연락처·이력이 공개 HTTP 응답에 없는지 확인한다. 저장 검증은 실패·취소·재시작·부분 가져오기 원본 보존을 포함한다. UI 검증은 손쉬운 사용 이름과 터치 영역, 큰 글자, 빈 상태 및 실제 화면 캡처를 포함한다. Ponytail은 복잡성만 검토하며 이 검증을 대체하지 않는다.

공통 조사 자료 이관 검증: 프로그램 28개·공고 30개·조직 26개, ID 중복 없음·조직 참조 및 이미지 파일 경로 유효. 웹 실행 제거는 2fc4875. 통합 초안 [PR #40](https://github.com/fixabley/dearby/pull/40).

## 2026-09-27 조율 checkout 실행

API 첫 구현 통합 후 Node 24.21.0: 깨끗한 `npm ci`, TCP HTTP 테스트 10/10, typecheck, lint, build 통과. SMTP는 테스트 sink이며 실제 수신 증거는 #42 미완료다. 공개 필드·철회·권한·멱등성·SQL rollback·부분 가져오기·재시작 보존을 확인했다.

CI는 [checkout](https://github.com/actions/checkout), [setup-node](https://github.com/actions/setup-node), [setup-java](https://github.com/actions/setup-java)의 현재 공식 사용법을 확인해 구성했다. macos-26 runner의 기본 Xcode는 [공식 이미지 목록](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md)에 따라 별도로 기록한다. 로컬 Xcode 27 검증과 GitHub runner 검증은 같은 결과로 취급하지 않는다. YAML 구문은 Ruby YAML로 확인했으며 원격 실행 결과는 아직 없다.

## 2026-09-27 06:24 KST 양 플랫폼 로컬 연결

iOS URLSession/AppState와 Android HttpClient/Repository가 같은 격리 API에 접속해 테스트 메일 OTP·프로필 저장·선택 공개 명함 발행·기기 명함 가져오기를 통과했다. 양방향 전달 후 양 앱의 wallet 응답에서 reciprocal=true를 확인했고 동일 요청 재전송 receipt도 일치했다. 실제 메일 발송이나 두 실기기 카메라 스캔 증거는 아니다. 플랫폼별 증거 파일은 각 담당 완료 보고 후 통합한다.

조율 코드 리뷰에서 Android 미확정 전송 A 뒤 B를 전송하면 A 재시도 키가 덮이는 경로를 발견해 수정·회귀 검사를 요청했다. 기본 반복 재시도 통과와 이 추가 경로의 완료를 구분한다. iOS QR 최종 캡처에서 승인된 배치와 차이를 발견해 카드 내부 공유 버튼·작은 메타데이터·하단 명함 목록 배치를 수정 중이다.
