# Android 구현 인계

## 현재 작업 — 2026-09-27 #38 첫 네이티브 수직 구현

이 checkout의 이전 Android 앱은 웹 전환 시 제거되어 새 Compose 앱을 구성했다. 과거 2026-09-20 검증 정본은 [보존 기록](../../apps/android/docs/archive/android-handoff-2026-09-20.md)이며 이번 검증이 아니다.

소유 범위 apps/android 및 이 문서, docs/workstreams/android.md. checkout `/Users/jominjun/Documents/dearby/dearby-android`, branch `fixabley/dearby-android`; worker `term_0c0f0f9c-8f0d-4c93-b1fe-4989b3ff3e31`, task `task_10fe5a67df42`, dispatch `ctx_8fdb85641ada`. 2026-09-27 KST 확인. 완료 후 세션 유지, push/PR/통합은 조율 담당.

구현: 5탭·프로필 연락처/활동 편집·공개 선택·QR/단독 확대/공유 메뉴·실제 HTTP 이메일 OTP·Room 기기 ID·Keystore 토큰·선택 가져오기·지갑 검색/영역/카드 전환·명시적 전송. 기본 서버 주소 미설정, 가짜 계정/명함 없음. 공개 선택/가져오기 초기값은 모두 선택 해제, 미선택/실패 ID 보존. 계정별 프로필 캐시, 로그인 시 기기 초안을 자동 업로드하지 않는다.

현재 실제 검증: Debug APK, 계측 APK, lintDebug, JVM12(실패0/skip0) 통과. Room/QR/Keystore 에뮬레이터 검증 진행 중; 최종 결과는 후속 업데이트한다. 세부 실행 방법과 의존성 근거는 [앱 README](../../apps/android/README.md), [구조](../../apps/android/ARCHITECTURE.md).

미완료: 운영 이메일/HTTPS #42, QR 공개 링크·등록 활동 조회·실기기 상호 스캔 #44/#43, 전체 서비스 카탈로그·푸시·캘린더·신청 #41/#36. 이슈 작성은 완료가 아니다. 등록 활동 선택은 API 부재로 직접 입력/선택 안 함만 제공한다. 카메라는 외부 카메라 촬영 결과 방식이며 연속 스캐너가 아니다. 상세 판단·검증 증거를 최종 인계에 갱신한다.
