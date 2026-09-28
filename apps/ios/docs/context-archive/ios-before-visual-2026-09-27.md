# iOS 활동 구현 인계 — issue #46

검증 시점: 2026-09-27 KST. checkout `/Users/jominjun/Documents/dearby/dearby-ios`, branch `feat/ios-activities` from `dcb590f`. 시작 시 clean 확인, 이전 `fixabley/dearby-ios`와 `6f090fa` 이력 보존. Terminal `term_a08d43fc-ce31-4077-9bac-a2446ed0b9a6`, Task `task_12d3471fcfb1`, Dispatch `ctx_a33d870a32de`. Coordinator `term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd`. 완료 후 세션 retain, push/PR/통합은 coordinator 담당.

이전 #37 인계는 [보존 문서](../../apps/ios/docs/context-archive/ios-before-activities-2026-09-27.md). 현재 기능 설명은 [활동 구현 계약](../../apps/ios/docs/catalog-implementation.md).

## 구현과 커밋

- `12fd90e` 실제 GET /v1/catalog, domain DTO/검증, atomic SwiftData cache/local library, 발견·저장·상세·native Safari와 신청 자기기록. failed refresh/commit 보존, corrupt cache 복구, 유효시각/마감/최대24시간을 클라이언트에서 재검사.
- `d4374b1` 등록·지난 활동/직접 입력/없음 교환 선택, QR URL context 보존, 받은 명함·가져오기 표시 및 활동 검색. 등록 ID와 직접 label 동시 전송 금지 회귀.
- 후속 검증 커밋: source-only prompt 없음, 나중에 명시적 버튼, Korean date, 얇은 신청 배너, root 수명에서 초기 refresh, native UI captures와 최종 문서. 최종 commit ID는 git log/worker_done을 확인한다.

## 실제 검증

정본: [이번 검증·명령·스크린샷](../../apps/ios/docs/evidence/catalog/README.md).

- 27 unit/domain/storage/contract tests, 16 Harmonize/SwiftSyntax 구조 검사, strict SwiftLint 통과.
- Debug 및 Release Simulator build 통과. 기본 ad-hoc 서명 유지, CODE_SIGNING_ALLOWED=NO 사용하지 않음.
- 실제 격리 API `http://127.0.0.1:52777`: 30개 활동, 현재 모집 if(kakao)26 1개, 실제 HTTP decode/관계검증·SwiftData 재열기 저장/신청 복원 통과.
- 활동 XCUITest 12:28:53 KST 통과, 54.784초/실패0: 출처 닫기 질문 없음, 신청 닫기 질문, 나중에 미변경, 직접 신청 기록과 체크 배너, 저장 조직·프로그램·신청 기록 재시작 복원, 신청하지 않음으로 수정. 원본11장 보존.
- 기존 실제 auth/profile/card HTTP 회귀 12:29:12 통과. 실제 메일 수신이 아닌 coordinator test mail sink 사용, 코드·토큰 비기록.
- 기존 profile/QR/wallet XCUITest 12:30:56 통과,28.174초/실패0. 편집/QR 확대·복귀/공개 필드/받은 명함 확인.
- 마지막 출처 화면에서 내부 validUntil 표시가 모집 마감과 혼동될 수 있어 화면 표시 한 줄만 제거. freshness 판정/DTO는 유지. 해당 화면 별도 캡처가 원본 전체 흐름 이미지보다 최신이다.
- UI 초기 실패 원인과 복구를 evidence README에 구분했다. 브라우저 한국어 닫기 선택자, cancel-role 나중에 미노출, 팝오버 등장 중 이른 tap, 가상화된 화면 밖 row 확인을 수정/검증했다. 혼합 unit/UI cleanup 지연은 대상 분리와 `-parallel-testing-enabled NO`로 해결했다. 중단/실패 번들은 성공 증거 아님.
- Ponytail 검토는 테스트용 임시 PNG 직접 기록·중복 진단 캡처만 제거하고 필요한 SwiftData/State/FSD 경계를 보존했다. 최종 추가 삭제 후보 없음.

## 남은 범위

이번 slice에 calendar/알림/외부 폼 autofill/운영 인증을 포함하지 않는다. 등록활동 selector는 domain/wire/링크·과거기록 표시 회귀를 확인했고 두 계정 간 등록활동 전송/전체 picker UI·VoiceOver/극대 글자는 이번 실행 범위 밖이다. #36 if(kakao) 외부 인증 이후 폼 검증, #42 운영 auth/HTTPS, #43 Universal Links/실기 권한 검증과 #41 전체 서비스는 남는다. 외부 신청/로그인/최종 제출을 자동 실행하지 않았고 사용자 신청 자기기록은 주최 측 접수·선정·결제 확인이 아니다.

개인정보·OTP·token은 문서/로그에 기록하지 않는다. localhost 테스트 mail sink를 사용할 경우 private fixture 준비 스크립트만 사용하고 실제 메일 수신으로 표현하지 않는다.
