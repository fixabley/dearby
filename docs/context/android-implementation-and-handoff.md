# Android 구현 인계

## 현재 상태 — 설계 단순화 완료 (2026-09-20)

사용자가 승인한 Android 대응 설계 단순화의 구현·검증·기능별 커밋을 완료했다. push/PR/merge는 메인 담당이며 메인 전달로 PR34가 게시됐다. 작업/검증 상세 정본은 [이번 검증](../../apps/android/docs/design-simplification/VERIFICATION.md), 실제 구조는 [ARCHITECTURE](../../apps/android/ARCHITECTURE.md)다.

### 변경과 결정

- `e370c03`: 카드 미사용 applicationSummary/locationSummary/applicationDateText와 계산 제거, 기존 일정/장소/AX·저장 입력 유지.
- `1bc9972`: 상세 미표시 provenance/categoryPath/relatedOrganizations/sources/evidence와 context 중복 ID/role 제거. 원본 NoticeModel/codec/출처·근거 검증은 보존했다.
- `9d107e5`: Settings가 유일한 동의·권한 요청 소유자. BusySession의 미호출 confirm/permissionResult/retry 및 Consent/Requesting 제거; 실제 foreground/취소/세대·상태 수명과 NoticeSession의 공유 repository/VM identity는 유지했다.
- `1740bda`: Shared 디자인 UI 직접 사용 유지·storage/network/OS 경로 금지 회귀, 내부 FavoriteNoticeState 진입점 정리. Swift식 Content/보조타입 이름/VM 선언 강제나 표시-only Feature/factory는 Android에 없어서 이식하지 않았다.
- 최종 CalendarEditor 회귀 정정: 앱 전체 READ_CALENDAR 미선언을 기대하던 기존 test를 exporter의 Intent flags=0·WRITE 미선언 계약으로 맞췄다. 메인이 받은 앞선 SHA는 재작성하지 않았다. 최종 SHA는 완료 보고/현재 HEAD를 따른다.

### 이번 검증과 한계

JVM80 실패/오류/skip0, Debug/계측 APK, lintDebug 오류0/경고13(최종 test 수정 후 재실행 포함), FSD101파일/자체회귀30 통과. 전체 전용5556 계측은 58중57통과/기존 CalendarEditor assertion1실패였다. 이를 정정한 뒤 해당 클래스2개 모두 통과했으며 전체58 재실행으로 기록하지 않는다. 초기 XML/HTML은 별도 로컬 build 폴더에 보존했다. 기존 UI·Room·permission 회귀를 축소하지 않았고 Ponytail diff 검토에서 추가 삭제 후보 없음.

실제 카드→Google Maps 장소명/정확한 좌표/pin 표시→같은 카드 복귀 확인. 캘린더 버튼은 Google Calendar의 계정 안내로 전환됐으며 로그인/저장하지 않았다. 실제 CalendarProvider 계정/반복 조회·편집·저장/동기화와 TalkBack 전체 탐색은 미실행이다. 증거/명령/이전 실패와 최종 재검증의 구분은 이번 검증 문서에만 상세 기록한다. #13/#14/#15 전체 후속은 별도다.

### 재개 지점과 소유권

- 2026-09-20 확인: origin/main PR32 `2362917`에서 시작한 `fixabley/dearby-android-design-simplification`, checkout `/Users/jominjun/Documents/dearby/dearby-android-design-simplification`. 메인 Dearby의 Orca 하위 worktree이며 별도 Git checkout이다.
- worker `term_dda3f25d-2d72-4efc-9c17-59970971ee35`, task `task_74dd1744d0d1`, dispatch `ctx_1d90d191c0f4`. 재개 시 라이브 상태를 다시 확인한다. 완료 보고 후 세션 유지 결정은 메인 담당이다.
- 전용 Dearby_Issue2_Test/API36 emulator-5556은 최종 Dearby 앱 실행 상태로 유지, Orca emulator helper 종료. 사용자의 다른 기기는 조작하지 않았다. 빌드/SDK 설정/로그/수동 스크린샷은 자기 checkout 안에 있다.
- 승인된 소유 범위 apps/android/** 및 이 역할 문서/Android workstream 외에는 쓰지 않았다. 다음 행동은 메인의 마지막 커밋 통합·PR34 검토/CI/병합이며 worker 구현 미완료는 없다. 기존 공개 커밋의 재작성/강제 push를 하지 않는다.

과거 카드 줄 증거는 [기존 카드 검증](../../apps/android/docs/card-schedules/CARD-LINES.md), 과거 캘린더 검증은 [이전 기록](../../apps/android/docs/calendar-busy/VERIFICATION.md)을 따른다. 과거 세션/기기/테스트 상태를 현재 상태로 해석하지 않는다.
