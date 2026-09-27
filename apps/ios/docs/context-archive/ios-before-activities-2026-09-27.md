# iOS 네이티브 첫 구현 인계

검증 시점: 2026-09-27 KST. 역할: issue #37 dispatched iOS worker. checkout `/Users/jominjun/Documents/dearby/dearby-ios`, branch `fixabley/dearby-ios`. Terminal `term_f4a86928-cda8-47c3-bff7-517d91c5f2d0`, Task `task_416545ae77df`, Dispatch `ctx_741102b1b057`. 이 값은 이번 세션 기록이며 재개 시 Orca 런타임에서 재확인한다. 완료 후 사용자 요청대로 세션 유지, push/PR/통합은 coordinator 담당이다.

이전 역할 문서는 [앱 소유 archive](../../apps/ios/docs/context-archive/ios-before-2026-09-27.md)에 보존했다. 과거 웹 전환 전 검사 결과를 이번 실행으로 재사용하지 않았다.

## 구현

- Xcode 27/Swift 6, iOS 18+ SwiftUI 앱과 실제 5탭. 발견/활동 저장은 서비스 미연결을 명시한다.
- 이메일 OTP 실제 HTTP 요청과 device-only Keychain. 미설정/오프라인/401은 성공으로 표시하지 않는다. 로그아웃/인증 만료가 계정 초안이나 게스트 ID를 지우지 않는다. 내 프로필/게시에는 로그인 필요.
- 마스터 프로필·6종 연락처·활동 이력 편집, 별도 draft, 명시적 SwiftData save/rollback·계정별 재시작 복원. 게시 시 strict PUT DTO로 개인 프로필 업로드 후 선택 ID만 POST /cards.
- 명함 생성 공개 아이콘/숨김/토스트, 이력 선택과 tri-state 전체 선택. 이름·직무 상단 유지, 연락처 44pt 안전 액션, 활동 타임라인.
- QR 카드 내 우상단 공유·링크/복사/사진 저장, 작고 확장 가능한 10/8pt 메타데이터, QR 단독 확대/복귀, 고정 하단 명함 선택·+ 생성 안내. 설치된 앱 간 dearby://card 링크와 맥락 파서. HTTPS 공유 도메인은 임의 생성하지 않음.
- 카메라/VisionKit와 사진/Vision 입력 코드, 공개 명함 조회 후 비로그인 저장 경고와 ID 영속 저장. 선택 가져오기 전체 선택/해제/나중에, 유효한 성공 receipt UUID만 제거; 실패·미선택·저장 실패 보존.
- 받은 명함 검색·서버 reciprocal 영역·명시적 내 명함 선택/보내기. API 성공만 전달 확인, 후속 목록 refresh 실패를 구분. 다른 명함을 선택해도 이전 모호한 전송의 account/payload별 requestId가 재시작 후 유지됨.
- 기존 FSD 의미·두 레이어/공개 API/pure UI/동위 slice 검사 복구. provider 조립, HomePage 탭 수명, 기능 상태, identity 모델 경계를 분리. [구조 설명](../../apps/ios/ARCHITECTURE.md).

## 기능별 커밋

1. `1b975a2` 계정 프로필·게스트 ID·전송 재시도 저장 기반과 회귀 테스트.
2. `9385d3d` SwiftUI 앱·인증·프로필/명함·QR·지갑·실제 HTTP 통합 및 UI 테스트.
3. `b5162e4` 선택 변경 뒤에도 모호한 전송의 요청 ID 보존.
4. `6cbb6f8` 고정 SwiftLint와 native Harmonize 구조 검사 복원.
5. `98db26a` QR 카드와 동일 높이 명함 타일의 기본 화면 배치 수정.
6. `6175d6f` HTTP decoding 단순화와 재생성 가능한 안정된 Xcode project UUID.
7. 최종 검증/인계 기록 커밋은 worker_done과 git log로 확인한다.

## 검증

[검증 정본·명령·실제 스크린샷](../../apps/ios/docs/evidence/README.md), [요약 로그](../../apps/ios/docs/evidence/verification.txt).

- 17 앱 단위/계약 테스트, 16 구조 테스트, SwiftLint 40파일 위반0.
- Debug 및 Release Simulator build, 실제 install/launch, guest/인증 XCUITest 각각 통과.
- 실제 임시 API `http://127.0.0.1:53634` + 격리 테스트 메일 sink로 네이티브 로그인/Keychain/프로필 PUT/선택 공개/GET/가져오기/wallet 조회 통과. 실제 이메일 수신 검증 아님.
- iOS→Android 전달, 같은 persisted request 두 번 replay 동일 receipt, Android→iOS 역방향 명함 reciprocal=true 확인. Android도 역방향 갱신을 확인했다고 coordinator가 전달했다.
- 최종 QR screenshot을 Vision으로 디코드해 실제 발행 cardId 일치 확인.
- **CI Simulator 테스트에서 CODE_SIGNING_ALLOWED=NO를 사용하지 않는다.** 기본 ad-hoc 서명으로 Keychain 검사 통과. 외부 Apple 개발자 계정은 사용하지 않았다.

## 남은 조건

#42 SMTP 실제 수신·운영 HTTPS, #43 Universal Links/HTTPS fallback·물리 QR/사진 권한 검증, #41 활동/알림/캘린더 등 전체 서비스 추적, #36 if(kakao) 실제 폼 차단. 등록 활동 선택은 현재 서비스 미연결이며 직접 활동명/없음만 가능하다. 큰 글자/VoiceOver·카드 제스처 충돌·새 명함 생성 후 선택·취소/오류의 전체 UI 왕복은 남아 있다. local API와 Simulator 증거를 운영·실기기 완료로 보지 않는다.

`ponytail-review` 적용 후 불필요한 AnyView/중복 store 보유를 없앴고, 마지막 검토에 추가 삭제 후보는 없었다. 전체 서비스나 issue #37의 모든 운영 검증 완료를 선언하지 않는다. 다음은 coordinator 통합/CI와 남은 실기기 UI·OS 검증이며 이 worker는 자기 checkout만 변경했다.
