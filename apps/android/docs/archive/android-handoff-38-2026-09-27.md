# Android 구현 인계

## 현재 상태 — 2026-09-27 #38 첫 네이티브 수직 구현

Android 첫 명함 수직 구현과 검증을 완료했다. 전체 서비스 출시 완료는 아니다. 과거 2026-09-20 결과는 [보존 기록](../../apps/android/docs/archive/android-handoff-2026-09-20.md)이며 이번 실행이 아니다.

- 5탭, 로그인 gate, OTP HTTP, 프로필/연락처/활동 편집, 명함 공개 선택, QR·단독 확대/공유/사진 해독, Room 기기 저장/선택 가져오기, 지갑 검색/영역/상하 선택, 명시적 전송과 Keystore 세션 구현.
- 명함 공개 선택·가져오기 기본값은 미선택. 실패/미선택 ID 보존, 계정별 cache, 실제 서버 성공에만 이관/전달 처리, 모호한 전송의 requestId Room 보존, safe contact actions와 날짜/역할 이력. 비로그인 내 프로필은 로그인 안내다.
- Page는 화면 State/콜백을 사용하고 providers가 도메인 매핑·의존성 수명을 구성한다. 이전 Android 구조 checker를 복원·적용했다. [구조](../../apps/android/ARCHITECTURE.md).

## 실제 검증

정본 [VERIFICATION.md](../../apps/android/docs/VERIFICATION.md), [실행 방법/버전 근거](../../apps/android/README.md), [로컬 API 재현](../../apps/android/docs/local-api-testing.md).

기본 URL 미설정 구성 Debug·unsigned Release·계측 APK와 lint 성공. JVM18 실패0/skip0, 실제 에뮬레이터11 실패0, FSD32소스/자체회귀24 통과. lint 오류0/경고15(버전 갱신/KTX/legacy backup 제안), 광범위 disable/baseline 없음. JBR25.0.2/Gradle9.3.1/API36 `Dearby_Issue2_Test` emulator-5554를 사용했다.

격리된 실제 API+private test mail sink를 사용한 Android repository 경로의 OTP·프로필·선택 공개·Room 저장/import·Android→iOS 전송과 동일 요청 replay·역방향 수신 reciprocal 확인도 통과했다. OTP/token 미출력/미커밋. 운영 이메일이나 로그인 화면 전체 타이핑 E2E와는 구분한다. 실제 서버 화면과 UI fixture 캡처를 evidence에서 분리했다.

Ponytail 검토 후 불필요한 AuthRepository DAO 의존을 제거했다. 공개/저장 실패/상태 경계는 보존했고 과도한 DI/인터페이스 계층은 추가하지 않았다. 초기 JUnit 반환형, Espresso API36 오류, 역방향 전송 전 assertion 실패와 수정 후 결과는 검증 정본에 남겼다.

## 남은 범위와 인계

#42 운영 이메일/HTTPS, #44/#43 공개 HTTPS 링크·App Links·등록 활동 API·실물 기기 스캔, #41/#36 활동/알림/캘린더/신청/자동입력 전체 서비스 후속. QR 카메라는 외부 촬영 결과 방식으로 실시간 scanner가 아니며 실물 카메라/SAF 저장 왕복/전체 TalkBack·큰 글자는 미검증이다. 서버 wallet 전체 오프라인 복원을 주장하지 않는다. 이슈 등록이 해결이나 전체 서비스 완료를 뜻하지 않는다.

소유: `/Users/jominjun/Documents/dearby/dearby-android`, branch `fixabley/dearby-android`; worker `term_0c0f0f9c-8f0d-4c93-b1fe-4989b3ff3e31`, task `task_10fe5a67df42`, dispatch `ctx_8fdb85641ada`. 2026-09-27 KST에 확인했으며 재개 시 런타임 재확인. apps/android와 이 문서, docs/workstreams/android.md 외에 수정하지 않았다. push/PR/통합은 조율 담당이며 커밋 SHA는 완료 보고와 git log를 따른다. 완료 후 세션을 유지하고 새 작업을 임의 시작하지 않는다.
