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

## 2026-09-27 시각 교정 진행 (Issue #50 / PR40)

- 배정: task_872cf36fa029 / ctx_46936e11509f, Android checkout만 수정. clean 확인 후 c86f809에서 feat/android-visual-fidelity 생성.
- 승인 근거: docs/design/native-visual-contract.md 및 approved PNG. 기본 Material 외형 지침보다 우선. 승인 로고 원본 PNG를 drawable-nodpi에 복사.
- 이번 실행 baseline: ScreenTest + CatalogScreenTest 14/14 PASS. Gradle 실행은 앱 제거로 파일을 지워 baseline APK 직접 install/instrument로 다시 캡처. `apps/android/evidence/visual-fidelity/before/fixtures`는 테스트 타깃 전용 데이터의 실제 Compose 렌더링이며 production 데이터가 아님.
- 공통 흰색/청록/10dp 버튼, 프로필 연락처 행·연표, 발견 좌측 썸네일 자리표시자/우측 정보, 상세 고정 하단 CTA 구현 중. API는 conference/club 분류·이미지 URL이 없어 실제 participation 타입 칩과 정직한 이미지 미제공 표시 사용.
- Debug/AndroidTest 빌드 1차 성공. 전체 회귀·Release·lint·최종 캡처는 아직 진행 중. 세션 유지 요청 유효.

### 중간 시각 리뷰 반영

- Root의 actual/reference 비교 후 QR 활동 선택 기본 접힘, 프로필 중복 추가 제거/청록 아바타, wallet의 0개 가져오기 숨김과 작은 새로고침, 명함 밖 상세/전달 액션을 반영.
- GitHub 심볼은 공식 primer/octicons의 mark-github-16 vector (MIT 원문 apps/android/docs/octicons-LICENSE.txt). 새 라이브러리 없음.
- 명함 묶음은 실제 인접 카드만 표시하며 큰 글자에 따라 겹친 헤더 간격을 늘림. 요약 이력 월 표시와 상세 원본 일자 구분. 상대 그룹 숫자는 distinct ownerId, 페이지 숫자는 카드 수.
- 공유 카드 조회와 기기 저장 분리: 성공한 공개 조회 후 프리뷰, 명시적 저장만 기존 GuestStore에 ID 추가. 인증/서버 전달/idempotency는 기존 계약 유지. 실제 외부 카메라·사진 기능만 제공.
- 이번 실행 기존 화면14 + rich fixture10 + 실제 Activity 흐름1 = 25/25 통과. 첫 full-app 실행은 snackbar가 새명함 클릭을 가린 테스트 실패였고 snackbar 사라짐을 기다려 재실행 성공. 최종 작은화면/넘김 회귀 및 artifact 정리는 진행 중.
- JVM28, FSD45 files/28 self-test, Debug/Release assemble, lintDebug/lintRelease 성공(기존 버전/KTX warning 17개씩, 오류0). UI 최종 조정 후 필요한 재실행 결과는 아래 완료 보고에 기록.

### Issue #52: 실제 Android 로그아웃 교정

- 실제 UI 회귀에서 logout이 `Invalid request`로 실패하여 계정 상태를 안전하게 유지하는 것을 확인. 단순 클릭 타이밍 문제가 아니었음.
- Android ServerSocket 기반 HTTP 검사에서 `HttpURLConnection`이 본문 없는 DELETE에도 `Content-Type: application/x-www-form-urlencoded`, `Content-Length: 0`을 자동 전송함을 재현. JVM 구현과 달라 기존 JVM 회귀만으로 잡히지 않음.
- Android HttpClient에서 bodyless DELETE에만 `Content-Type: text/plain; charset=UTF-8`을 명시. payload는 0바이트로 유지하며 JSON `{}`나 서버 계약 변경 없이 Fastify의 빈 JSON/미지원 form 파서를 회피.
- Android 네이티브 framing 회귀 1/1 PASS. 실제 API 로그아웃 → 비로그인 공유 명함 → 저장 경고 취소 → 로그인 취소 복귀 → 실제 이메일 OTP 인증 → 보내기 선택(자동 전송 없음) → 공유 카드로 취소 복귀 1/1 PASS (13초).
- OTP는 앱 private file로 전달·즉시 삭제, 응답/토큰/private 파일은 evidence에 포함하지 않음. Root는 Issue #52에 공통 추적.
