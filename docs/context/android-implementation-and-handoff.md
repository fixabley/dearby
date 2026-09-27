# Android 구현 인계

## 현재 상태 — 2026-09-27 #47 활동 통합

`dcb590f`에서 깨끗한 자기 checkout을 확인한 뒤 `feat/android-activities`를 만들었다. 기존 `fixabley/dearby-android` 브랜치/커밋은 보존했다. 소유 범위는 apps/android와 이 문서, docs/workstreams/android.md이며 shared/root/다른 checkout 수정·push·PR·main merge는 하지 않았다.

- Public `GET /v1/catalog` 실제 클라이언트, flat Organization/Program/Activity DTO와 참조 검증, origin별 Room 문서 cache. refresh 실패는 이전 자료를 유지하며 유효기간이 지난 활동은 발견에 표시하지 않는다. 다음 의미 있는 시각 경계와 app resume에서 재평가한다.
- 발견/저장 placeholder를 실제 모집 목록·프로그램/조직 북마크로 교체. 로딩·오류·빈 결과·보관 자료를 구분한다. 상세는 출처 확인 시각, 일정/대상/지원 조건/참가 방식/비용/장소와 정직한 미확인을 표시한다. 홈 검색/내 명함 영역은 추가하지 않았다.
- 로컬 북마크와 신청 본인 기록은 Room commit 후에만 State 반영, 실패 시 원래 상태 유지, 재시작 복원. 공개되지 않은 외부 접수·선정·결제를 성공 처리하지 않는다.
- Application WebView 닫기→신청함/안 함/나중에. Source 링크는 prompt 없음. exact banner “이 활동은 이미 신청한 활동이에요.”와 check icon은 얇게 고정되며 수정/공식 사이트 행동 제공. 로그인 scheme 실패는 시스템 브라우저 안내; 외부 인증/자동입력 #36은 미완료다.
- QR/direct-send의 등록 활동(과거 포함)/직접 입력/없음 선택. registered 요청은 activityId와 null label, direct는 null activityId와 label. 지갑/가져오기 표시는 catalog 이름을 조합하며 모르는 ID는 “활동 정보 확인 필요”, UUID를 이름으로 노출하지 않는다.
- 독립 CatalogViewModel 소유, provider에서 기존 계정 ViewModel과 Room/HTTP 수명 공유. Page는 State/콜백, feature가 OS WebView 효과를 소유한다. 기존 profile/QR/wallet 회귀를 유지했다.

## 검증과 증거

정본은 [앱 검증](../../apps/android/docs/VERIFICATION.md), [구조](../../apps/android/ARCHITECTURE.md), [실행](../../apps/android/README.md), [Ponytail](../../apps/android/docs/ponytail-review.md).

JVM28, emulator18, FSD43 sources/28 자체회귀, Debug·unsigned Release·AndroidTest build와 lint(error0/warning17)를 실행했다. 기본 URL 미설정 빌드와 실제 API URL 빌드를 구분했다. 실제 API30 activities/모집1 응답으로 repository+Room, guest discovery/detail/save/application-close/later/self-report/과거 context, 강제 종료 재시작 복원을 별도3 테스트로 검증했다. 캡처는 apps/android/docs/evidence/catalog에 있으며 `real-*`와 `fixture-*`가 구분된다. 외부 로그인/실제 제출은 하지 않았고 신청함은 테스트의 명시적 본인 기록일 뿐이다.

Ponytail에서 중복 factory/database 수명과 미사용 loaded 상태를 줄였고 불필요한 DI/저장소 인터페이스는 추가하지 않았다. 시각 만료 polling은 다음 경계 coroutine으로 바꿨다. 정확성·보안·접근성 검증은 Ponytail와 별도 실행했다. 전체 서비스 완료로 확대하지 않는다.

## 남은 범위와 재개

#36 외부 인증/검증된 폼 자동입력, OS 캘린더/푸시, #42 운영 이메일/HTTPS, #44/#43 public links와 실기기 스캔은 별도 후속이다. 모든 TalkBack/실제 휴대폰/운영 서버 검증을 완료했다고 주장하지 않는다. #38 이전 검증은 [보존 인계](../../apps/android/docs/archive/android-handoff-38-2026-09-27.md)에 있다.

실시간 연결 확인 시점 2026-09-27 KST: checkout `/Users/jominjun/Documents/dearby/dearby-android`; worker `term_a4ea575a-6b18-4abd-87ea-a0cc5503b02a`, task `task_e3bf1ed7f722`, dispatch `ctx_e1187d1fd488`, coordinator `term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd`. 재개 때 런타임·git log를 다시 확인한다. 조율 담당의 격리 API52777은 worker가 종료하지 않는다. 통합은 조율 담당 소유이고 사용자는 완료 후 세션 retain을 요청했다.
